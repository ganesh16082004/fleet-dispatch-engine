package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.cache.DriverLocationCache;
import com.ganesh.fleetdispatch.graph.NodeId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
public class RedisTestController {
    private final DriverLocationCache driverLocationCache;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String kafkaTopic;

    public RedisTestController(
            DriverLocationCache driverLocationCache,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${fleet.kafka.topic:fleet.events}") String kafkaTopic) {
        this.driverLocationCache = driverLocationCache;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
    }

    @PostMapping("/api/v1/redis/test")
    public Map<String, Object> testRedis() {
        long driverId = 999_999L;
        NodeId node = new NodeId(12345L);

        driverLocationCache.put(driverId, node);
        NodeId stored = driverLocationCache.get(driverId)
                .orElseThrow(() -> new IllegalStateException("Redis write succeeded but value was not readable"));
        driverLocationCache.remove(driverId);

        return Map.of(
                "status", "UP",
                "backend", "redis-cloud",
                "driverId", driverId,
                "storedNode", stored.value(),
                "message", "Spring Boot successfully wrote to and read from Redis Cloud");
    }

    @PostMapping("/api/v1/kafka/test")
    public Map<String, Object> testKafka() {
        String eventId = UUID.randomUUID().toString();
        String key = "kafka-test";
        String event = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"KAFKA_TEST\",\"timestamp\":"
                + Instant.now().toEpochMilli() + ",\"message\":\"Fleet Dispatch Engine Kafka test\"}";

        kafkaTemplate.send(kafkaTopic, key, event);

        return Map.of(
                "status", "PUBLISHED",
                "topic", kafkaTopic,
                "eventId", eventId,
                "message", "Kafka event published to Confluent Cloud");
    }

    @KafkaListener(topics = "${fleet.kafka.topic:fleet.events}", groupId = "${KAFKA_CONSUMER_GROUP:fleet-dispatch-engine}")
    public void consumeKafkaEvent(String event) {
        System.out.println("[KafkaConsumer] Received event: " + event);
    }
}
