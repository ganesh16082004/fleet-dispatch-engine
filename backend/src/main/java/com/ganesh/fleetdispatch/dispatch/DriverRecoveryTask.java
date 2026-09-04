package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;

/** Recovery work item created when a driver fails while carrying an order. */
public record DriverRecoveryTask(
        long failedDriverId,
        long orderId,
        NodeId handoffNode,
        long createdAtMillis
) {
    public DriverRecoveryTask {
        if (failedDriverId < 0) {
            throw new IllegalArgumentException("failedDriverId must be non-negative");
        }
        if (orderId < 0) {
            throw new IllegalArgumentException("orderId must be non-negative");
        }
        Objects.requireNonNull(handoffNode, "handoffNode must not be null");
        if (createdAtMillis < 0) {
            throw new IllegalArgumentException("createdAtMillis must be non-negative");
        }
    }
}
