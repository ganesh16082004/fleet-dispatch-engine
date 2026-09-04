package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;

/** A pickup or drop-off stop for an active order. */
public record RouteStop(long orderId, RouteStopType type, NodeId nodeId) {
    public RouteStop {
        if (orderId < 0) {
            throw new IllegalArgumentException("orderId must be non-negative");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
    }
}
