package com.ganesh.fleetdispatch.routing;

import java.util.Objects;

public record RoadEdge(
        String id,
        String fromNodeId,
        String toNodeId,
        double distanceMeters,
        double speedKph
) {
    public RoadEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromNodeId, "fromNodeId");
        Objects.requireNonNull(toNodeId, "toNodeId");
        if (id.isBlank() || fromNodeId.isBlank() || toNodeId.isBlank()) {
            throw new IllegalArgumentException("edge identifiers must not be blank");
        }
        if (!Double.isFinite(distanceMeters) || distanceMeters <= 0) {
            throw new IllegalArgumentException("distanceMeters must be positive and finite");
        }
        if (!Double.isFinite(speedKph) || speedKph <= 0) {
            throw new IllegalArgumentException("speedKph must be positive and finite");
        }
    }

    public double baseTravelTimeSeconds() {
        return distanceMeters / (speedKph * 1000.0 / 3600.0);
    }
}
