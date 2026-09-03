package com.ganesh.fleetdispatch.graph;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.ganesh.fleetdispatch.domain.Location;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryRoadNetworkLoaderTest {

    @Test
    void returnsConfiguredGraph() {
        NodeId first = new NodeId(1);
        NodeId second = new NodeId(2);

        RoadNode firstNode = new RoadNode(first, new Location(12.9716, 77.5946));
        RoadNode secondNode = new RoadNode(second, new Location(12.9720, 77.5950));
        RoadEdge edge = new RoadEdge(first, second, 60.0, 6.0);
        RoadGraph graph = new RoadGraph(
                Map.of(first, firstNode, second, secondNode),
                List.of(edge));

        RoadNetworkLoader loader = new InMemoryRoadNetworkLoader(graph);

        assertSame(graph, loader.load());
    }
}
