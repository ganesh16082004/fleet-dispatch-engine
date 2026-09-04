package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/** Immutable result of a successful picked-up order recovery. */
public record RecoveryAssignment(
        long orderId,
        long driverId,
        Route driverToHandoffRoute,
        Route handoffToDropoffRoute) {
    public RecoveryAssignment {
        if (orderId < 0) {
            throw new IllegalArgumentException("orderId must be non-negative");
        }
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        Objects.requireNonNull(driverToHandoffRoute, "driverToHandoffRoute");
        Objects.requireNonNull(handoffToDropoffRoute, "handoffToDropoffRoute");
    }

    public double totalTravelTimeSeconds() {
        return driverToHandoffRoute.totalTravelTimeSeconds()
                + handoffToDropoffRoute.totalTravelTimeSeconds();
    }

    public double totalDistanceMeters() {
        return driverToHandoffRoute.totalDistanceMeters()
                + handoffToDropoffRoute.totalDistanceMeters();
    }
}
