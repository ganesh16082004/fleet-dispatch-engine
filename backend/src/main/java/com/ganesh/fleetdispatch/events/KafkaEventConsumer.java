package com.ganesh.fleetdispatch.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganesh.fleetdispatch.persistence.ProcessedEventDocument;
import com.ganesh.fleetdispatch.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class KafkaEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${fleet.kafka.topic:fleet.events}",
            groupId = "${KAFKA_CONSUMER_GROUP:fleet-dispatch-engine}")
    public void consume(String event) {
        UUID eventId = parseEventId(event);

        try {
            processedEventRepository.insert(ProcessedEventDocument.of(eventId));
        } catch (DuplicateKeyException duplicate) {
            log.info("Ignoring duplicate fleet event: {}", eventId);
            return;
        }

        log.info("Processed fleet event: {}", event);
    }

    private UUID parseEventId(String event) {
        try {
            JsonNode root = objectMapper.readTree(event);
            JsonNode eventId = root.get("eventId");
            if (eventId == null || eventId.asText().isBlank()) {
                throw new IllegalArgumentException("Fleet event is missing eventId");
            }
            return UUID.fromString(eventId.asText());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid fleet event payload", exception);
        }
    }
}
