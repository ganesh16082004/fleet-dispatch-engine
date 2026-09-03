package com.ganesh.fleetdispatch.domain;

import java.util.Objects;

public record Rider(
        String id,
        Location location,
        RiderStatus status,
        int capacity
) {
    public Rider {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(status, "status");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
    }
}
