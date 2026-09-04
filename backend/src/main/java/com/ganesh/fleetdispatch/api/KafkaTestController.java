package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.events.KafkaEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class KafkaTestController {
    private final KafkaEventPublisher eventPublisher;
    private final String topic;

    public KafkaTestController(
            KafkaEventPublisher eventPublisher,
            @Value("${fleet.kafka.topic:fleet.events}") String topic) {
        this.eventPublisher = eventPublisher;
        this.topic = topic;
    }

    @PostMapping("/api/v1/kafka/test")
    public Map<String, Object> testKafka() {
        UUID eventId = eventPublisher.publishTest();

        return Map.of(
                "status", "PUBLISHED",
                "topic", topic,
                "eventId", eventId,
                "message", "Kafka event published through FleetEvent publisher");
    }
}
