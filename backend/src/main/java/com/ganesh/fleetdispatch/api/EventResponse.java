package com.ganesh.fleetdispatch.api;

import java.time.Instant;
import java.util.Map;

public record EventResponse(
        String eventId,
        String eventType,
        String aggregateId,
        String aggregateType,
        Instant createdAt,
        Instant publishedAt,
        int attempts,
        String status,
        Map<String, Object> payload) {
}
