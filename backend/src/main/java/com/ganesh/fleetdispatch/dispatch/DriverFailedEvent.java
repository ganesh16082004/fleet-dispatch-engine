package com.ganesh.fleetdispatch.dispatch;

public record DriverFailedEvent(long driverId, long timestampMillis) implements DomainEvent {
    public DriverFailedEvent {
        if (driverId < 0 || timestampMillis < 0) {
            throw new IllegalArgumentException("driverId and timestampMillis must be non-negative");
        }
    }
}
