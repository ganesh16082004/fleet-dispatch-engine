package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;
import java.util.UUID;

/** A driver location update received from a live tracking session. */
public record DriverLocationUpdate(
        long driverId,
        UUID sessionId,
        long sequenceNumber,
        NodeId node,
        long timestampMillis
) {
    public DriverLocationUpdate {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be non-negative");
        }
        Objects.requireNonNull(node, "node must not be null");
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be non-negative");
        }
    }
}
