package com.ganesh.fleetdispatch.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

/** Decodes the small JSON contract used by driver location WebSocket clients. */
public final class DriverLocationMessageCodec {
    private final ObjectMapper objectMapper;

    public DriverLocationMessageCodec() {
        this(new ObjectMapper());
    }

    public DriverLocationMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public DriverLocationPayload decode(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Location message must not be blank");
        }

        try {
            JsonNode root = objectMapper.readTree(message);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Location message must be a JSON object");
            }

            return new DriverLocationPayload(
                    requiredLong(root, "sequenceNumber"),
                    requiredLong(root, "nodeId"),
                    requiredLong(root, "timestampMillis"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid location JSON", e);
        }
    }

    private static long requiredLong(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException("Missing or non-integral field: " + field);
        }
        return value.longValue();
    }
}
