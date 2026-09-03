package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;

/** Resolves a geographic location to the nearest node in a road graph. */
public interface NodeLocator {
    NodeId findNearest(Location location);
}
