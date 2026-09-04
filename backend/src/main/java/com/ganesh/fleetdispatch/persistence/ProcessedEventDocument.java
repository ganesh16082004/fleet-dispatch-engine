package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Unique event id record used to make Kafka consumer processing idempotent. */
@Document(collection = "processed_events")
public record ProcessedEventDocument(
        @Id String id,
        String eventId,
        Instant processedAt
) {
    public static ProcessedEventDocument of(UUID eventId) {
        return new ProcessedEventDocument(eventId.toString(), eventId.toString(), Instant.now());
    }
}
