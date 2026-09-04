package com.ganesh.fleetdispatch.dispatch;

/** JSON payload accepted from a driver tracking connection. Driver and session identity are server-side. */
public record DriverLocationPayload(
        long sequenceNumber,
        long nodeId,
        long timestampMillis
) {
    public DriverLocationPayload {
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId must be non-negative");
        }
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be non-negative");
        }
    }
}
