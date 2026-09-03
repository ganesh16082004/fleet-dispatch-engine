package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.domain.Location;

import java.util.Objects;

public record RoadNode(String id, Location location) {
    public RoadNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
