package com.ganesh.fleetdispatch.dispatch;

public record OrderPickedUpEvent(long orderId, long driverId, long timestampMillis) implements DomainEvent {
    public OrderPickedUpEvent {
        if (orderId < 0 || driverId < 0 || timestampMillis < 0) {
            throw new IllegalArgumentException("orderId, driverId and timestampMillis must be non-negative");
        }
    }
}
