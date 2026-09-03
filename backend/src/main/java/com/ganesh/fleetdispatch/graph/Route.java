package com.ganesh.fleetdispatch.graph;

import java.util.List;
import java.util.Objects;

/** Immutable routing result containing the traversed nodes and total cost. */
public record Route(List<NodeId> nodes, double totalTravelTimeSeconds, double totalDistanceMeters) {
    public Route {
        Objects.requireNonNull(nodes, "nodes");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Route must contain at least one node");
        }
        if (!Double.isFinite(totalTravelTimeSeconds) || totalTravelTimeSeconds < 0) {
            throw new IllegalArgumentException("totalTravelTimeSeconds must be finite and non-negative");
        }
        if (!Double.isFinite(totalDistanceMeters) || totalDistanceMeters < 0) {
            throw new IllegalArgumentException("totalDistanceMeters must be finite and non-negative");
        }
        nodes = List.copyOf(nodes);
    }
}
