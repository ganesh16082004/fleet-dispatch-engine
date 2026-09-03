package com.ganesh.fleet.core.graph;

import java.util.Objects;

/** Stable identifier for a node in the road graph. */
public record NodeId(long value) {
    public NodeId {
        if (value < 0) {
            throw new IllegalArgumentException("Node id must be non-negative");
        }
    }
}
