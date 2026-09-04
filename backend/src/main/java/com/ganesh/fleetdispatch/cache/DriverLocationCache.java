package com.ganesh.fleetdispatch.cache;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Optional;

public interface DriverLocationCache {
    void put(long driverId, NodeId node);

    Optional<NodeId> get(long driverId);

    void remove(long driverId);
}
