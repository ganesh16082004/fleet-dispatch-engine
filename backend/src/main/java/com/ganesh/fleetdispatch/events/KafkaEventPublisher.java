package com.ganesh.fleetdispatch.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class KafkaEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${fleet.kafka.topic:fleet.events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
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
            kafkaTemplate.send(topic, aggregateId, json);
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Kafka event: " + eventType, exception);
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
