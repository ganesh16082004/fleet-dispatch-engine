package com.ganesh.fleetdispatch.benchmark;

import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverStateStore;
import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Simple reproducible benchmark for driver candidate discovery.
 *
 * <p>Run with assertions enabled using:
 * {@code java -ea ...DriverCandidateDiscoveryBenchmark}.
 * This is intentionally dependency-free; JMH can be introduced later when we
 * start doing JVM-level performance tuning.</p>
 */
public final class DriverCandidateDiscoveryBenchmark {
    private static final double RADIUS_METERS = 1_000.0;
    private static final int MAX_CANDIDATES = 20;
    private static final int WARMUP_ITERATIONS = 10;
    private static final int MEASURED_ITERATIONS = 30;

    private DriverCandidateDiscoveryBenchmark() {
    }

    public static void main(String[] args) {
        for (int driverCount : new int[]{10_000, 100_000}) {
            BenchmarkFixture fixture = buildFixture(driverCount, 42L);
            benchmark("full-scan", fixture, fixture::fullScan);
            benchmark("grid-index", fixture, fixture::gridQuery);
            verifyEquivalentResults(fixture);
            System.out.println();
        }
    }

    private static void benchmark(
            String name,
            BenchmarkFixture fixture,
            Supplier<List<Driver>> operation) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            consume(operation.get());
        }

        long[] samples = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            List<Driver> result = operation.get();
            consume(result);
            samples[i] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(samples);
        long median = samples[samples.length / 2];
        long p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1];

        System.out.printf(
                "%s: median=%.3f ms, p95=%.3f ms%n",
                name,
                median / 1_000_000.0,
                p95 / 1_000_000.0);
    }

    private static void verifyEquivalentResults(BenchmarkFixture fixture) {
        List<Driver> expected = fixture.fullScan();
        List<Driver> actual = fixture.gridQuery();

        if (!expected.stream().map(Driver::id).toList()
                .equals(actual.stream().map(Driver::id).toList())) {
            throw new AssertionError("Grid query returned a different candidate set from full scan");
        }

        System.out.printf("correctness: %d candidate(s), identical result set%n", actual.size());
    }

    private static void consume(List<Driver> drivers) {
        long checksum = 0;
        for (Driver driver : drivers) {
            checksum = checksum * 31 + driver.id();
        }
        BlackHole.VALUE = checksum;
    }

    private static BenchmarkFixture buildFixture(int driverCount, long seed) {
        Random random = new Random(seed);
        Map<NodeId, RoadNode> nodes = new java.util.HashMap<>(driverCount + 1);
        NodeId queryNodeId = new NodeId(0L);
        nodes.put(queryNodeId, new RoadNode(queryNodeId, new Location(12.9716, 77.5946)));

        List<Driver> drivers = new ArrayList<>(driverCount);
        for (int i = 1; i <= driverCount; i++) {
            NodeId nodeId = new NodeId(i);
            double latitude = 12.91 + random.nextDouble() * 0.12;
            double longitude = 77.53 + random.nextDouble() * 0.12;
            nodes.put(nodeId, new RoadNode(nodeId, new Location(latitude, longitude)));
            drivers.add(new Driver(i, nodeId, DriverStatus.AVAILABLE));
        }

        RoadGraph graph = new RoadGraph(nodes, List.of());
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        for (Driver driver : drivers) {
            store.addDriver(driver);
        }

        Location query = nodes.get(queryNodeId).location();
        return new BenchmarkFixture(store, query, drivers);
    }

    private record BenchmarkFixture(
            InMemoryDriverStateStore store,
            Location query,
            List<Driver> allDrivers) {

        List<Driver> fullScan() {
            List<DriverDistance> candidates = new ArrayList<>();
            for (Driver driver : allDrivers) {
                if (driver.status() != DriverStatus.AVAILABLE) {
                    continue;
                }
                Location driverLocation = storeLocation(driver);
                double distance = haversineMeters(query, driverLocation);
                if (distance <= RADIUS_METERS) {
                    candidates.add(new DriverDistance(driver, distance));
                }
            }

            return candidates.stream()
                    .sorted(Comparator
                            .comparingDouble(DriverDistance::distanceMeters)
                            .thenComparingLong(item -> item.driver().id()))
                    .limit(MAX_CANDIDATES)
                    .map(DriverDistance::driver)
                    .toList();
        }

        List<Driver> gridQuery() {
            return store.getAvailableDriversNear(query, RADIUS_METERS, MAX_CANDIDATES);
        }

        private Location storeLocation(Driver driver) {
            return store
                    .getLocation(driver.currentNode())
                    .orElseThrow(() -> new IllegalStateException("Missing node: " + driver.currentNode()));
        }
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

    private record DriverDistance(Driver driver, double distanceMeters) {
    }

    private static final class BlackHole {
        private static volatile long VALUE;

        private BlackHole() {
        }
    }
}
