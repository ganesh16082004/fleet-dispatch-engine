package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;

public record Driver(
        long id,
        NodeId currentNode,
        DriverStatus status
) {
    public Driver {
        if (id < 0) {
            throw new IllegalArgumentException("Driver id must be non-negative");
        }

        Objects.requireNonNull(currentNode, "currentNode must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
