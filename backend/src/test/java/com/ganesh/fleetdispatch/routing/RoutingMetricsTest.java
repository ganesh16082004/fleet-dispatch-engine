package com.ganesh.fleetdispatch.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingMetricsTest {

    @Test
    void recordsRoutingWorkCounters() {
        RoutingMetrics metrics = new RoutingMetrics();

        metrics.recordNodeExpanded();
        metrics.recordNodeExpanded();
        metrics.recordEdgeRelaxed();
        metrics.recordEdgeRelaxed();
        metrics.recordEdgeRelaxed();

        assertEquals(2, metrics.nodesExpanded());
        assertEquals(3, metrics.edgesRelaxed());
    }
}
