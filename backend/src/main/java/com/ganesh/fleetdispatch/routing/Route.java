package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.List;
import java.util.Objects;

public record Route(List<NodeId> nodes, double totalTravelTimeSeconds, double totalDistanceMeters) {
    public Route {
        Objects.requireNonNull(nodes, "nodes");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("route must contain at least one node");
        }
        nodes = List.copyOf(nodes);
        if (totalTravelTimeSeconds < 0 || !Double.isFinite(totalTravelTimeSeconds)) {
            throw new IllegalArgumentException("totalTravelTimeSeconds must be finite and non-negative");
        }
        if (totalDistanceMeters < 0 || !Double.isFinite(totalDistanceMeters)) {
            throw new IllegalArgumentException("totalDistanceMeters must be finite and non-negative");
        }
    }
}
