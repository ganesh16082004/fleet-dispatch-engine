package com.ganesh.fleetdispatch.dispatch;

public record OrderAssignedEvent(long orderId, long driverId, long timestampMillis) implements DomainEvent {
    public OrderAssignedEvent {
        if (orderId < 0 || driverId < 0 || timestampMillis < 0) {
            throw new IllegalArgumentException("orderId, driverId and timestampMillis must be non-negative");
        }
    }
}
