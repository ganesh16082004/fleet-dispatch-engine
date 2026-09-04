package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

public record RecoveryRequestedEvent(
        long failedDriverId,
        long orderId,
        NodeId handoffNode,
        long timestampMillis) implements DomainEvent {
    public RecoveryRequestedEvent {
        if (failedDriverId < 0 || orderId < 0 || timestampMillis < 0) {
            throw new IllegalArgumentException("ids and timestampMillis must be non-negative");
        }
        if (handoffNode == null) {
            throw new NullPointerException("handoffNode must not be null");
        }
    }
}
