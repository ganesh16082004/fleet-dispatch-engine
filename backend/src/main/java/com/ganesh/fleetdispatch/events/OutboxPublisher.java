package com.ganesh.fleetdispatch.events;

import com.ganesh.fleetdispatch.persistence.EventOutboxDocument;
import com.ganesh.fleetdispatch.persistence.EventOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Publishes durable outbox records to Kafka and retries failures with backoff. */
@Service
public class OutboxPublisher {
    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;

    public OutboxPublisher(
            EventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${fleet.kafka.topic:fleet.events}") String topic,
            @Value("${fleet.outbox.base-backoff-ms:1000}") long baseBackoffMillis,
            @Value("${fleet.outbox.max-backoff-ms:60000}") long maxBackoffMillis) {
        if (baseBackoffMillis <= 0 || maxBackoffMillis < baseBackoffMillis) {
            throw new IllegalArgumentException("Invalid outbox backoff configuration");
        }
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.baseBackoffMillis = baseBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    @Scheduled(fixedDelayString = "${fleet.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        List<EventOutboxDocument> pending = outboxRepository
                .findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now());

        for (EventOutboxDocument event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(EventOutboxDocument event) {
        try {
            kafkaTemplate.send(topic, event.aggregateId(), event.eventJson()).get(30, TimeUnit.SECONDS);
            outboxRepository.save(event.markPublished(Instant.now()));
        } catch (Exception exception) {
            long backoff = calculateBackoff(event.attempts());
            Instant nextAttemptAt = Instant.now().plusMillis(backoff);
            outboxRepository.save(event.markFailed(nextAttemptAt, safeError(exception)));
        }
    }

    private long calculateBackoff(int attempts) {
        long multiplier = 1L << Math.min(attempts, 16);
        return Math.min(maxBackoffMillis, baseBackoffMillis * multiplier);
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
