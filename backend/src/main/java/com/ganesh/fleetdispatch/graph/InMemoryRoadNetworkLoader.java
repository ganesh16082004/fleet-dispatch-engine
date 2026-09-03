package com.ganesh.fleetdispatch.graph;

import java.util.Objects;

/** Deterministic loader backed by an already-built graph, primarily for tests and local experiments. */
public final class InMemoryRoadNetworkLoader implements RoadNetworkLoader {
    private final RoadGraph graph;

    public InMemoryRoadNetworkLoader(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @Override
    public RoadGraph load() {
        return graph;
    }
}
