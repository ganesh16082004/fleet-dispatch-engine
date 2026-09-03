package com.ganesh.fleetdispatch.graph;

/** Stable identifier for a node in the road network. */
public record NodeId(long value) {
    public NodeId {
        if (value < 0) {
            throw new IllegalArgumentException("node id must be non-negative");
        }
    }
}
