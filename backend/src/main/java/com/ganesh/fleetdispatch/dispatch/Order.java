package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;

public record Order(
        long id,
        NodeId pickupNode,
        NodeId dropoffNode,
        long requestTimestamp,
        OrderStatus status
) {
    public Order {
        if (id < 0) {
            throw new IllegalArgumentException("Order id must be non-negative");
        }

        Objects.requireNonNull(pickupNode, "pickupNode must not be null");
        Objects.requireNonNull(dropoffNode, "dropoffNode must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (requestTimestamp < 0) {
            throw new IllegalArgumentException(
                    "requestTimestamp must be non-negative"
            );
        }
    }
}
