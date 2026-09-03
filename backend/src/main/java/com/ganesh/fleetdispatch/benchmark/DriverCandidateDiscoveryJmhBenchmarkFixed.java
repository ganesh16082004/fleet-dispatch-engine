package com.ganesh.fleetdispatch.benchmark;

import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverStateStore;
import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** JMH benchmark for full-scan versus grid-backed driver candidate discovery. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DriverCandidateDiscoveryJmhBenchmarkFixed {
    private static final double RADIUS_METERS = 1_000.0;
    private static final int MAX_CANDIDATES = 20;

    @Benchmark
    public List<Driver> fullScan(BenchmarkState state) {
        return state.fullScan();
    }

    @Benchmark
    public List<Driver> gridQuery(BenchmarkState state) {
        return state.gridQuery();
    }

    @Benchmark
    @Group("mixedGrid")
    @GroupThreads(7)
    public List<Driver> concurrentGridLookup(BenchmarkState state) {
        return state.gridQuery();
    }

    @Benchmark
    @Group("mixedGrid")
    @GroupThreads(1)
    public void concurrentLocationUpdate(BenchmarkState state) {
        state.moveRandomDriver();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({"10000", "100000"})
        public int driverCount;

        private InMemoryDriverStateStore store;
        private RoadGraph graph;
        private Location query;
        private List<Driver> allDrivers;

        @Setup(Level.Trial)
        public void setup() {
            Random random = new Random(42L);
            Map<NodeId, RoadNode> nodes = new java.util.HashMap<>(driverCount + 1);
            query = new Location(12.9716, 77.5946);
            NodeId queryNodeId = new NodeId(0L);
            nodes.put(queryNodeId, new RoadNode(queryNodeId, query));

            List<Driver> drivers = new ArrayList<>(driverCount);
            for (int i = 1; i <= driverCount; i++) {
                NodeId nodeId = new NodeId(i);
                double latitude = 12.91 + random.nextDouble() * 0.12;
                double longitude = 77.53 + random.nextDouble() * 0.12;
                nodes.put(nodeId, new RoadNode(nodeId, new Location(latitude, longitude)));
                drivers.add(new Driver(i, nodeId, DriverStatus.AVAILABLE));
            }

            graph = new RoadGraph(nodes, List.of());
            store = new InMemoryDriverStateStore(graph);
            for (Driver driver : drivers) {
                store.addDriver(driver);
            }
            allDrivers = List.copyOf(drivers);
        }

        List<Driver> gridQuery() {
            return store.getAvailableDriversNear(query, RADIUS_METERS, MAX_CANDIDATES);
        }

        List<Driver> fullScan() {
            List<DriverDistance> candidates = new ArrayList<>();
            for (Driver driver : allDrivers) {
                Driver current = store.getDriver(driver.id()).orElse(null);
                if (current == null || current.status() != DriverStatus.AVAILABLE) {
                    continue;
                }
                RoadNode node = graph.node(current.currentNode());
                if (node == null) {
                    continue;
                }
                double distance = haversineMeters(query, node.location());
                if (distance <= RADIUS_METERS) {
                    candidates.add(new DriverDistance(current, distance));
                }
            }

            return candidates.stream()
                    .sorted(Comparator.comparingDouble(DriverDistance::distanceMeters)
                            .thenComparingLong(item -> item.driver().id()))
                    .limit(MAX_CANDIDATES)
                    .map(DriverDistance::driver)
                    .toList();
        }

        void moveRandomDriver() {
            int index = ThreadLocalRandom.current().nextInt(allDrivers.size());
            Driver driver = allDrivers.get(index);
            NodeId newNode = new NodeId(ThreadLocalRandom.current().nextInt(1, driverCount + 1));
            store.updateLocation(driver.id(), newNode);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            store = null;
            graph = null;
            query = null;
            allDrivers = null;
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

    private record DriverDistance(Driver driver, double distanceMeters) {
    }
}
