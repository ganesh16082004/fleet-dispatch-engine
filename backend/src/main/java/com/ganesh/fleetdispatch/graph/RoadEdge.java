package com.ganesh.fleetdispatch.graph;

import java.util.Objects;

/** Directed road segment weighted by travel time. */
public record RoadEdge(NodeId from, NodeId to, double distanceMeters, double travelTimeSeconds) {
    public RoadEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (distanceMeters <= 0 || !Double.isFinite(distanceMeters)) {
            throw new IllegalArgumentException("distanceMeters must be positive and finite");
        }
        if (travelTimeSeconds <= 0 || !Double.isFinite(travelTimeSeconds)) {
            throw new IllegalArgumentException("travelTimeSeconds must be positive and finite");
        }
    }
}
