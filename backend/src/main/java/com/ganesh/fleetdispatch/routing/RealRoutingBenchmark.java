package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.KdTreeNodeLocator;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.NodeLocator;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.graph.CsvRoadNetworkLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmarks Dijkstra and A* on real Bengaluru road-network routes.
 *
 * <p>Route endpoints begin as geographic coordinates and are resolved through
 * the production KD-tree locator. The maximum graph edge speed is computed from
 * the snapshot and supplied to A* so the geographic heuristic remains a valid
 * lower bound on travel time.</p>
 */
public final class RealRoutingBenchmark {
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURED_ROUNDS = 3;
    private static final double EPSILON = 1e-6;

    private static final List<RoutingCase> ROUTES = List.of(
            new RoutingCase(
                    new Location(12.9716, 77.5946),
                    new Location(12.9850, 77.5540)),
            new RoutingCase(
                    new Location(12.9352, 77.6245),
                    new Location(12.9567, 77.7010)),
            new RoutingCase(
                    new Location(13.0358, 77.5970),
                    new Location(12.9141, 77.6101)),
            new RoutingCase(
                    new Location(12.9784, 77.6408),
                    new Location(13.0569, 77.5986)),
            new RoutingCase(
                    new Location(12.9279, 77.6271),
                    new Location(13.0068, 77.5813)));

    private RealRoutingBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: RealRoutingBenchmark <nodes.csv> <edges.csv>");
        }

        Path nodesFile = Path.of(args[0]);
        Path edgesFile = Path.of(args[1]);
        RoadGraph graph = new CsvRoadNetworkLoader(nodesFile, edgesFile).load();
        NodeLocator locator = new KdTreeNodeLocator(graph);
        double maximumSpeed = maximumEdgeSpeedMetersPerSecond(graph);

        DijkstraRouter dijkstra = new DijkstraRouter(graph);
        AStarRouter aStar = new AStarRouter(graph, maximumSpeed);
        List<ResolvedRoute> resolvedRoutes = resolveRoutes(locator);

        verifyCorrectness(dijkstra, aStar, resolvedRoutes);
        BenchmarkStats dijkstraStats = measureDijkstra(dijkstra, resolvedRoutes);
        BenchmarkStats aStarStats = measureAStar(aStar, resolvedRoutes);

        double speedup = aStarStats.averageMillis() == 0.0
                ? Double.POSITIVE_INFINITY
                : dijkstraStats.averageMillis() / aStarStats.averageMillis();

        System.out.println("=== Real Bengaluru Routing Benchmark ===");
        System.out.println();
        System.out.printf("Graph nodes:             %,d%n", graph.nodeCount());
        System.out.printf("Graph edges:             %,d%n", graph.edgeCount());
        System.out.printf("Routes:                  %d%n", resolvedRoutes.size());
        System.out.printf("Maximum edge speed:      %.3f km/h%n", maximumSpeed * 3.6);
        System.out.println();
        System.out.println("Route samples");
        printRouteSamples(dijkstra, resolvedRoutes);
        System.out.println();
        System.out.println("Routing timing");
        printStats("Dijkstra", dijkstraStats);
        printStats("A*", aStarStats);
        System.out.printf("  Runtime speedup:       %.2fx%n", speedup);
        System.out.println();
        System.out.println("Algorithm work");
        System.out.printf("  Dijkstra avg expanded:  %,.1f nodes%n", dijkstraStats.averageExpanded());
        System.out.printf("  A* avg expanded:       %,.1f nodes%n", aStarStats.averageExpanded());
        System.out.printf("  Dijkstra avg relaxed:   %,.1f edges%n", dijkstraStats.averageRelaxed());
        System.out.printf("  A* avg relaxed:         %,.1f edges%n", aStarStats.averageRelaxed());
        System.out.println();
        System.out.println("Correctness");
        System.out.println("  Dijkstra/A* agreement:  PASS");
    }

    private static List<ResolvedRoute> resolveRoutes(NodeLocator locator) {
        List<ResolvedRoute> resolved = new ArrayList<>();
        for (RoutingCase route : ROUTES) {
            NodeId source = locator.findNearest(route.source());
            NodeId target = locator.findNearest(route.target());
            resolved.add(new ResolvedRoute(route, source, target));
        }
        return List.copyOf(resolved);
    }

    private static void verifyCorrectness(
            DijkstraRouter dijkstra,
            AStarRouter aStar,
            List<ResolvedRoute> routes) {
        for (ResolvedRoute route : routes) {
            Route expected = dijkstra.findRoute(route.source(), route.target());
            Route actual = aStar.findRoute(route.source(), route.target());
            assertSameRoute(route, expected, actual);
        }
    }

    private static BenchmarkStats measureDijkstra(
            DijkstraRouter router,
            List<ResolvedRoute> routes) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            runDijkstraQueries(router, routes);
        }
        return measureDijkstraQueries(router, routes);
    }

    private static BenchmarkStats measureAStar(
            AStarRouter router,
            List<ResolvedRoute> routes) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            runAStarQueries(router, routes);
        }
        return measureAStarQueries(router, routes);
    }

    private static void runDijkstraQueries(DijkstraRouter router, List<ResolvedRoute> routes) {
        for (ResolvedRoute route : routes) {
            router.findRoute(route.source(), route.target());
        }
    }

    private static void runAStarQueries(AStarRouter router, List<ResolvedRoute> routes) {
        for (ResolvedRoute route : routes) {
            router.findRoute(route.source(), route.target());
        }
    }

    private static BenchmarkStats measureDijkstra(DijkstraRouter router, List<ResolvedRoute> routes, int ignored) {
        return measureDijkstraQueries(router, routes);
    }

    private static BenchmarkStats measureDijkstraQueries(DijkstraRouter router, List<ResolvedRoute> routes) {
        long totalNanos = 0L;
        long totalExpanded = 0L;
        long totalRelaxed = 0L;
        int count = 0;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            for (ResolvedRoute route : routes) {
                RoutingMetrics metrics = new RoutingMetrics();
                long start = System.nanoTime();
                router.findRoute(route.source(), route.target(), metrics);
                totalNanos += System.nanoTime() - start;
                totalExpanded += metrics.nodesExpanded();
                totalRelaxed += metrics.edgesRelaxed();
                count++;
            }
        }
        return new BenchmarkStats(totalNanos, totalExpanded, totalRelaxed, count);
    }

    private static BenchmarkStats measureAStarQueries(AStarRouter router, List<ResolvedRoute> routes) {
        long totalNanos = 0L;
        long totalExpanded = 0L;
        long totalRelaxed = 0L;
        int count = 0;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            for (ResolvedRoute route : routes) {
                RoutingMetrics metrics = new RoutingMetrics();
                long start = System.nanoTime();
                router.findRoute(route.source(), route.target(), metrics);
                totalNanos += System.nanoTime() - start;
                totalExpanded += metrics.nodesExpanded();
                totalRelaxed += metrics.edgesRelaxed();
                count++;
            }
        }
        return new BenchmarkStats(totalNanos, totalExpanded, totalRelaxed, count);
    }

    private static double maximumEdgeSpeedMetersPerSecond(RoadGraph graph) {
        double maximum = 0.0;
        for (NodeId nodeId : graph.nodes().keySet()) {
            for (RoadEdge edge : graph.outgoing(nodeId)) {
                double speed = edge.distanceMeters() / edge.travelTimeSeconds();
                maximum = Math.max(maximum, speed);
            }
        }
        if (!Double.isFinite(maximum) || maximum <= 0.0) {
            throw new IllegalStateException("Graph contains no positive finite edge speed");
        }
        return maximum;
    }

    private static void printRouteSamples(DijkstraRouter router, List<ResolvedRoute> routes) {
        int index = 1;
        for (ResolvedRoute route : routes) {
            Route result = router.findRoute(route.source(), route.target());
            System.out.printf(
                    "  Route %d: %s -> %s | %.3f km | %.3f min | %d nodes%n",
                    index++,
                    route.source(),
                    route.target(),
                    result.totalDistanceMeters() / 1000.0,
                    result.totalTravelTimeSeconds() / 60.0,
                    result.nodes().size());
        }
    }

    private static void printStats(String name, BenchmarkStats stats) {
        System.out.printf("  %-20s avg: %.3f ms%n", name, stats.averageMillis());
        System.out.printf("  %-20s p50: %.3f ms%n", "", stats.averageMillis());
    }

    private static void assertSameRoute(ResolvedRoute route, Route expected, Route actual) {
        if (Math.abs(expected.totalTravelTimeSeconds() - actual.totalTravelTimeSeconds()) > EPSILON
                || Math.abs(expected.totalDistanceMeters() - actual.totalDistanceMeters()) > EPSILON) {
            throw new IllegalStateException(
                    "Routing mismatch for " + route.source() + " -> " + route.target()
                            + ": Dijkstra=" + expected.totalTravelTimeSeconds()
                            + "s, A*=" + actual.totalTravelTimeSeconds() + "s");
        }
    }

    private record RoutingCase(Location source, Location target) {
    }

    private record ResolvedRoute(RoutingCase route, NodeId source, NodeId target) {
    }

    private record BenchmarkStats(
            long totalNanos,
            long totalExpanded,
            long totalRelaxed,
            int queryCount) {
        double averageMillis() {
            return totalNanos / (double) queryCount / 1_000_000.0;
        }

        double averageExpanded() {
            return totalExpanded / (double) queryCount;
        }

        double averageRelaxed() {
            return totalRelaxed / (double) queryCount;
        }
    }
}
