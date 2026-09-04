package com.ganesh.fleetdispatch.events;

import java.util.Objects;
import java.util.UUID;

/** Stable Kafka event envelope shared by producers and consumers. */
public record FleetEvent(
        UUID eventId,
        FleetEventType eventType,
        long timestamp,
        String aggregateId,
        String aggregateType,
        Object payload
) {
    public FleetEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(payload, "payload");
        if (aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        if (aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
    }
}
