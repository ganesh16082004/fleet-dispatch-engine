package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/** Immutable result of a successful dispatch decision. */
public record DispatchAssignment(
        long orderId,
        long driverId,
        Route driverToPickupRoute
) {
    public DispatchAssignment {
        if (orderId < 0) {
            throw new IllegalArgumentException("orderId must be non-negative");
        }
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        Objects.requireNonNull(driverToPickupRoute, "driverToPickupRoute");
    }
}
