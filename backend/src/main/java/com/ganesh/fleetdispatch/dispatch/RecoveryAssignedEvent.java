package com.ganesh.fleetdispatch.dispatch;

public record RecoveryAssignedEvent(
        long orderId,
        long replacementDriverId,
        long timestampMillis) implements DomainEvent {
    public RecoveryAssignedEvent {
        if (orderId < 0 || replacementDriverId < 0 || timestampMillis < 0) {
            throw new IllegalArgumentException("ids and timestampMillis must be non-negative");
        }
    }
}
