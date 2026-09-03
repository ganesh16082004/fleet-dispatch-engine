package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H3DriverSpatialIndexTest {
    private static final int DRIVER_COUNT = 2_000;
    private static final int MAX_CANDIDATES = 25;
    private static final double RADIUS_METERS = 2_000.0;

    @Test
    void h3LookupMatchesGridLookupAcrossDeterministicQueries() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore grid = new InMemoryDriverStateStore(graph, SpatialIndexType.GRID);
        InMemoryDriverStateStore h3 = new InMemoryDriverStateStore(graph, SpatialIndexType.H3);

        for (int i = 0; i < DRIVER_COUNT; i++) {
            Driver driver = new Driver(10_000L + i, new NodeId(1_000L + i), DriverStatus.AVAILABLE);
            grid.addDriver(driver);
            h3.addDriver(driver);
        }

        Random random = new Random(2026L);
        for (int query = 0; query < 100; query++) {
            Location location = new Location(
                    12.90 + random.nextDouble() * 0.16,
                    77.50 + random.nextDouble() * 0.16);

            List<Long> expected = grid.getAvailableDriversNear(
                            location, RADIUS_METERS, MAX_CANDIDATES)
                    .stream()
                    .map(Driver::id)
                    .toList();
            List<Long> actual = h3.getAvailableDriversNear(
                            location, RADIUS_METERS, MAX_CANDIDATES)
                    .stream()
                    .map(Driver::id)
                    .toList();

            assertEquals(expected, actual, "Mismatch for query " + query + " at " + location);
        }
    }

    private RoadGraph graph() {
        Map<NodeId, RoadNode> nodes = new HashMap<>();
        List<RoadNode> nodeList = new ArrayList<>();

        for (int i = 0; i < DRIVER_COUNT; i++) {
            NodeId nodeId = new NodeId(1_000L + i);
            double latitude = 12.90 + (i % 100) * 0.0015;
            double longitude = 77.50 + (i / 100) * 0.0015;
            RoadNode node = new RoadNode(nodeId, new Location(latitude, longitude));
            nodes.put(nodeId, node);
            nodeList.add(node);
        }

        return new RoadGraph(nodes, List.of());
    }
}
