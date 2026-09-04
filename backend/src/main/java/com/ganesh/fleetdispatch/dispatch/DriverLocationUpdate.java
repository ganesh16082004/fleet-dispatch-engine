package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;

/** A driver location heartbeat received from the live tracking channel. */
public record DriverLocationUpdate(
        long driverId,
        long sequenceNumber,
        NodeId node,
        long timestampMillis
) {
    public DriverLocationUpdate {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be non-negative");
        }
        Objects.requireNonNull(node, "node must not be null");
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be non-negative");
        }
    }
}
