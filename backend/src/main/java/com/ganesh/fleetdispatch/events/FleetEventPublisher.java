package com.ganesh.fleetdispatch.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganesh.fleetdispatch.persistence.EventOutboxDocument;
import com.ganesh.fleetdispatch.persistence.EventOutboxRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Domain-facing publisher that durably records events before Kafka delivery. */
@Service
public class FleetEventPublisher {
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FleetEventPublisher(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public UUID publish(FleetEventType eventType, String aggregateId, String aggregateType, Object payload) {
        UUID eventId = UUID.randomUUID();
        FleetEvent event = new FleetEvent(
                eventId,
                eventType,
                System.currentTimeMillis(),
                aggregateId,
                aggregateType,
                payload);

        try {
            String json = objectMapper.writeValueAsString(event);
            outboxRepository.save(EventOutboxDocument.pending(
                    eventId,
                    eventType.name(),
                    aggregateId,
                    aggregateType,
                    json));
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event: " + eventType, exception);
        }
    }

    public UUID publishTest() {
        return publish(
                FleetEventType.KAFKA_TEST,
                "kafka-test",
                "SYSTEM",
                java.util.Map.of("message", "Fleet Dispatch Engine Kafka test"));
    }
}
