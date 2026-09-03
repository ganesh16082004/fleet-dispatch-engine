package com.ganesh.fleet.core.graph;

import com.ganesh.fleet.core.model.Location;

import java.util.Objects;

/** A physical intersection or routable point in the road network. */
public record RoadNode(NodeId id, Location location) {
    public RoadNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
    }
}
