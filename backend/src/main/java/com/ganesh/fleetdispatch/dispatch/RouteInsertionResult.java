package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/** Best route insertion for adding one order to a driver's active route. */
public record RouteInsertionResult(
        DriverRoutePlan plan,
        Route route,
        double incrementalTravelTimeSeconds,
        double incrementalDistanceMeters,
        double newOrderDeliveryEtaSeconds,
        double maxExistingDeliveryEtaIncreaseSeconds) {
    public RouteInsertionResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(route, "route");
        if (!Double.isFinite(incrementalTravelTimeSeconds) || incrementalTravelTimeSeconds < 0) {
            throw new IllegalArgumentException("incrementalTravelTimeSeconds must be finite and non-negative");
        }
        if (!Double.isFinite(incrementalDistanceMeters) || incrementalDistanceMeters < 0) {
            throw new IllegalArgumentException("incrementalDistanceMeters must be finite and non-negative");
        }
        if (!Double.isFinite(newOrderDeliveryEtaSeconds) || newOrderDeliveryEtaSeconds < 0) {
            throw new IllegalArgumentException("newOrderDeliveryEtaSeconds must be finite and non-negative");
        }
        if (!Double.isFinite(maxExistingDeliveryEtaIncreaseSeconds)
                || maxExistingDeliveryEtaIncreaseSeconds < 0) {
            throw new IllegalArgumentException(
                    "maxExistingDeliveryEtaIncreaseSeconds must be finite and non-negative");
        }
    }
}
