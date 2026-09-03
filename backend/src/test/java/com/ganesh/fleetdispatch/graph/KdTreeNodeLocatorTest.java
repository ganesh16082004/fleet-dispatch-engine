package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KdTreeNodeLocatorTest {

    @Test
    void matchesBruteForceLocatorAcrossRandomQueries() {
        Map<NodeId, RoadNode> nodes = Map.of(
                new NodeId(1), new RoadNode(new NodeId(1), new Location(12.9700, 77.5900)),
                new NodeId(2), new RoadNode(new NodeId(2), new Location(12.9710, 77.5940)),
                new NodeId(3), new RoadNode(new NodeId(3), new Location(12.9700, 77.5980)),
                new NodeId(4), new RoadNode(new NodeId(4), new Location(12.9750, 77.5930)),
                new NodeId(5), new RoadNode(new NodeId(5), new Location(12.9650, 77.5960)));

        RoadGraph graph = new RoadGraph(nodes, List.of());
        NodeLocator bruteForce = new BruteForceNodeLocator(graph);
        NodeLocator kdTree = new KdTreeNodeLocator(graph);

        Random random = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            Location query = new Location(
                    12.965 + random.nextDouble() * 0.010,
                    77.590 + random.nextDouble() * 0.008);
            assertEquals(bruteForce.findNearest(query), kdTree.findNearest(query));
        }
    }

    @Test
    void returnsExactNodeWhenLocationMatches() {
        NodeId nodeId = new NodeId(7);
        Location location = new Location(12.9716, 77.5946);
        RoadGraph graph = new RoadGraph(
                Map.of(nodeId, new RoadNode(nodeId, location)),
                List.of());

        KdTreeNodeLocator locator = new KdTreeNodeLocator(graph);

        assertEquals(nodeId, locator.findNearest(location));
    }

    @Test
    void rejectsEmptyGraph() {
        RoadGraph graph = new RoadGraph(Map.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> new KdTreeNodeLocator(graph));
    }

    @Test
    void rejectsNullLocation() {
        NodeId nodeId = new NodeId(1);
        RoadGraph graph = new RoadGraph(
                Map.of(nodeId, new RoadNode(nodeId, new Location(12.97, 77.59))),
                List.of());

        KdTreeNodeLocator locator = new KdTreeNodeLocator(graph);

        assertThrows(NullPointerException.class, () -> locator.findNearest(null));
    }
}
