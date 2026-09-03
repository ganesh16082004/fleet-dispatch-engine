package com.ganesh.fleetdispatch.routing;

/** Mutable per-query counters used by routing benchmarks and diagnostics. */
public final class RoutingMetrics {
    private long nodesExpanded;
    private long edgesRelaxed;

    public void recordNodeExpanded() {
        nodesExpanded++;
    }

    public void recordEdgeRelaxed() {
        edgesRelaxed++;
    }

    public long nodesExpanded() {
        return nodesExpanded;
    }

    public long edgesRelaxed() {
        return edgesRelaxed;
    }
}
