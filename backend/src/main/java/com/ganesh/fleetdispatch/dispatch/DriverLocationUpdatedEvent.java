package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import java.util.UUID;

public record DriverLocationUpdatedEvent(
        long driverId,
        UUID sessionId,
        long sequenceNumber,
        NodeId node,
        long timestampMillis) implements DomainEvent {
    public DriverLocationUpdatedEvent {
        if (driverId < 0 || sequenceNumber < 0 || timestampMillis < 0) {
            throw new IllegalArgumentException("driverId, sequenceNumber and timestampMillis must be non-negative");
        }
        if (sessionId == null || node == null) {
            throw new NullPointerException("sessionId and node must not be null");
        }
    }
}
