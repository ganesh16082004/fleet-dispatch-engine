package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/** Ranked replacement-driver option for a picked-up delivery recovery task. */
public record RecoveryCandidate(
        Driver driver,
        Route driverToHandoffRoute,
        Route handoffToDropoffRoute) {
    public RecoveryCandidate {
        Objects.requireNonNull(driver, "driver");
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
