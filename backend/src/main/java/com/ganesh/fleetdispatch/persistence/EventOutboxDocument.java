package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Durable event record written in the same Mongo transaction as domain state. */
@Document(collection = "event_outbox")
public record EventOutboxDocument(
        @Id String id,
        UUID eventId,
        String eventType,
        String aggregateId,
        String aggregateType,
        String eventJson,
        Instant createdAt,
        Instant publishedAt,
        int attempts,
        Instant nextAttemptAt,
        String lastError
) {
    public static EventOutboxDocument pending(
            UUID eventId,
            String eventType,
            String aggregateId,
            String aggregateType,
            String eventJson) {
        Instant now = Instant.now();
        return new EventOutboxDocument(
                eventId.toString(),
                eventId,
                eventType,
                aggregateId,
                aggregateType,
                eventJson,
                now,
                null,
                0,
                now,
                null);
    }

    public EventOutboxDocument markPublished(Instant publishedAt) {
        return new EventOutboxDocument(
                id,
                eventId,
                eventType,
                aggregateId,
                aggregateType,
                eventJson,
                createdAt,
                publishedAt,
                attempts,
                nextAttemptAt,
                lastError);
    }

    public EventOutboxDocument markFailed(Instant nextAttemptAt, String error) {
        return new EventOutboxDocument(
                id,
                eventId,
                eventType,
                aggregateId,
                aggregateType,
                eventJson,
                createdAt,
                null,
                attempts + 1,
                nextAttemptAt,
                error);
    }
}
