package com.ganesh.fleetdispatch.domain;

import java.time.Instant;
import java.util.Objects;

public record Order(
        String id,
        String restaurantId,
        Location restaurantLocation,
        Location customerLocation,
        Instant createdAt,
        Instant promisedBy,
        int itemCount,
        OrderStatus status
) {
    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(restaurantLocation, "restaurantLocation");
        Objects.requireNonNull(customerLocation, "customerLocation");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(promisedBy, "promisedBy");
        Objects.requireNonNull(status, "status");
        if (id.isBlank() || restaurantId.isBlank()) {
            throw new IllegalArgumentException("identifiers must not be blank");
        }
        if (itemCount <= 0) {
            throw new IllegalArgumentException("itemCount must be positive");
        }
        if (promisedBy.isBefore(createdAt)) {
            throw new IllegalArgumentException("promisedBy must not precede createdAt");
        }
    }
}
