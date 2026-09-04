package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/** A route-aware driver candidate for an order offer. */
public record OfferCandidate(
        Driver driver,
        Route route,
        double incrementalTravelTimeSeconds,
        double incrementalDistanceMeters) {
    public OfferCandidate {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(route, "route");
        if (!Double.isFinite(incrementalTravelTimeSeconds) || incrementalTravelTimeSeconds < 0.0) {
            throw new IllegalArgumentException("incrementalTravelTimeSeconds must be finite and non-negative");
        }
        if (!Double.isFinite(incrementalDistanceMeters) || incrementalDistanceMeters < 0.0) {
            throw new IllegalArgumentException("incrementalDistanceMeters must be finite and non-negative");
        }
    }
}
