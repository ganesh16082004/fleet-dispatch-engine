package com.ganesh.fleetdispatch.events;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Domain-facing event publisher used by application services.
 * Delegates transport concerns to the Kafka publisher.
 */
@Service
public class FleetEventPublisher {
    private final KafkaEventPublisher kafkaEventPublisher;

    public FleetEventPublisher(KafkaEventPublisher kafkaEventPublisher) {
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    public UUID publish(FleetEventType eventType, String aggregateId, String aggregateType, Object payload) {
        return kafkaEventPublisher.publish(eventType, aggregateId, aggregateType, payload);
    }

    public UUID publishTest() {
        return kafkaEventPublisher.publishTest();
    }
}
