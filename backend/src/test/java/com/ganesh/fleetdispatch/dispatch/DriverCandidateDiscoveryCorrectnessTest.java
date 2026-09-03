package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverCandidateDiscoveryCorrectnessTest {
    private static final double RADIUS_METERS = 1_000.0;
    private static final int MAX_CANDIDATES = 20;

    @Test
    void gridQueryMatchesFullScanAcrossDeterministicQueries() {
        Fixture fixture = fixture(5_000, 42L);
        Random random = new Random(123L);

        for (int query = 0; query < 50; query++) {
            Location location = new Location(
                    12.91 + random.nextDouble() * 0.12,
                    77.53 + random.nextDouble() * 0.12);

            List<Long> expected = fixture.fullScan(location).stream().map(Driver::id).toList();
            List<Long> actual = fixture.gridQuery(location).stream().map(Driver::id).toList();

            assertEquals(expected, actual, "Mismatch at query " + query + " for location " + location);
        }
    }

    private static Fixture fixture(int driverCount, long seed) {
        Random random = new Random(seed);
        HashMap<NodeId, RoadNode> nodes = new HashMap<>(driverCount);
        List<Driver> drivers = new ArrayList<>(driverCount);

        for (int i = 1; i <= driverCount; i++) {
            NodeId nodeId = new NodeId(i);
            Location location = new Location(
                    12.91 + random.nextDouble() * 0.12,
                    77.53 + random.nextDouble() * 0.12);
            nodes.put(nodeId, new RoadNode(nodeId, location));
            drivers.add(new Driver(i, nodeId, DriverStatus.AVAILABLE));
        }

        RoadGraph graph = new RoadGraph(nodes, List.of());
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        drivers.forEach(store::addDriver);
        return new Fixture(graph, store, drivers);
    }

    private record Fixture(
            RoadGraph graph,
            InMemoryDriverStateStore store,
            List<Driver> drivers) {

        List<Driver> fullScan(Location query) {
            return drivers.stream()
                    .map(driver -> new Candidate(driver, haversineMeters(query,
                            graph.node(driver.currentNode()).location())))
                    .filter(candidate -> candidate.distance() <= RADIUS_METERS)
                    .sorted(Comparator
                            .comparingDouble(Candidate::distance)
                            .thenComparingLong(candidate -> candidate.driver().id()))
                    .limit(MAX_CANDIDATES)
                    .map(Candidate::driver)
                    .toList();
        }

        List<Driver> gridQuery(Location query) {
            return store.getAvailableDriversNear(query, RADIUS_METERS, MAX_CANDIDATES);
        }
    }

    private record Candidate(Driver driver, double distance) {
    }

    private static double haversineMeters(Location a, Location b) {
        double phi1 = Math.toRadians(a.latitude());
        double phi2 = Math.toRadians(b.latitude());
        double dPhi = Math.toRadians(b.latitude() - a.latitude());
        double dLambda = Math.toRadians(b.longitude() - a.longitude());

        double sinPhi = Math.sin(dPhi / 2.0);
        double sinLambda = Math.sin(dLambda / 2.0);
        double h = sinPhi * sinPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
        return 6_371_000.0 * 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    }
}
