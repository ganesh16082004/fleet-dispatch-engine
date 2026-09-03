package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;
import java.util.Objects;

public record RoadNode(NodeId id, Location location) {
    public RoadNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
    }
}
