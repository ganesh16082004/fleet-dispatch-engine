package com.ganesh.fleetdispatch.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganesh.fleetdispatch.persistence.EventOutboxDocument;
import com.ganesh.fleetdispatch.persistence.EventOutboxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EventService {
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EventService(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public List<EventResponse> recent(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return outboxRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .limit(boundedLimit)
                .map(this::toResponse)
                .toList();
    }

    private EventResponse toResponse(EventOutboxDocument event) {
        Map<String, Object> payload = Map.of();
        try {
            JsonNode root = objectMapper.readTree(event.eventJson());
            JsonNode payloadNode = root.get("payload");
            if (payloadNode != null && payloadNode.isObject()) {
                payload = objectMapper.convertValue(payloadNode, Map.class);
            }
        } catch (Exception ignored) {
            // Event metadata remains available even if legacy payload JSON cannot be decoded.
        }

        String status = event.publishedAt() != null
                ? "PUBLISHED"
                : event.attempts() > 0 ? "FAILED_RETRYING" : "PENDING";

        return new EventResponse(
                event.eventId(),
                event.eventType(),
                event.aggregateId(),
                event.aggregateType(),
                event.createdAt(),
                event.publishedAt(),
                event.attempts(),
                status,
                payload);
    }
}
