package com.ganesh.fleetdispatch.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable event envelope. Event id enables idempotent processing later. */
public record DomainEvent(
        UUID eventId,
        EventType type,
        Instant occurredAt,
        String aggregateId,
        long sequence,
        Object payload
) {
    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        if (aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
    }
}
