package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BruteForceNodeLocatorTest {

    @Test
    void findsNearestNode() {
        NodeId west = new NodeId(1);
        NodeId center = new NodeId(2);
        NodeId east = new NodeId(3);

        RoadGraph graph = new RoadGraph(
                Map.of(
                        west, new RoadNode(west, new Location(12.9700, 77.5900)),
                        center, new RoadNode(center, new Location(12.9710, 77.5940)),
                        east, new RoadNode(east, new Location(12.9700, 77.5980))),
                List.of());

        BruteForceNodeLocator locator = new BruteForceNodeLocator(graph);

        assertEquals(center, locator.findNearest(new Location(12.9711, 77.5941)));
    }

    @Test
    void returnsExactNodeWhenLocationMatches() {
        NodeId nodeId = new NodeId(7);
        Location location = new Location(12.9716, 77.5946);
        RoadGraph graph = new RoadGraph(
                Map.of(nodeId, new RoadNode(nodeId, location)),
                List.of());

        BruteForceNodeLocator locator = new BruteForceNodeLocator(graph);

        assertEquals(nodeId, locator.findNearest(location));
    }

    @Test
    void rejectsNullLocation() {
        NodeId nodeId = new NodeId(1);
        RoadGraph graph = new RoadGraph(
                Map.of(nodeId, new RoadNode(nodeId, new Location(12.97, 77.59))),
                List.of());

        BruteForceNodeLocator locator = new BruteForceNodeLocator(graph);

        assertThrows(NullPointerException.class, () -> locator.findNearest(null));
    }
}
