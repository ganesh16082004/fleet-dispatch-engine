package com.ganesh.fleetdispatch.dispatch;

/** Immutable domain event emitted by the dispatch engine. */
public sealed interface DomainEvent
        permits DriverLocationUpdatedEvent,
                DriverFailedEvent,
                OrderAssignedEvent,
                OrderPickedUpEvent,
                OrderCompletedEvent,
                RecoveryRequestedEvent,
                RecoveryAssignedEvent {
    long timestampMillis();
}
