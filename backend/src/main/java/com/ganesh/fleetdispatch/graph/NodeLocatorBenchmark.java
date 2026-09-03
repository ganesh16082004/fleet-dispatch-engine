package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;

import java.nio.file.Path;
import java.util.List;

/** Benchmark the brute-force geographic nearest-node lookup on a real graph snapshot. */
public final class NodeLocatorBenchmark {
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 10;

    private static final List<Location> BENCHMARK_LOCATIONS = List.of(
            new Location(12.9716, 77.5946),
            new Location(12.9352, 77.6245),
            new Location(13.0358, 77.5970),
            new Location(12.9784, 77.6408),
            new Location(12.9279, 77.6271),
            new Location(13.0068, 77.5813),
            new Location(12.9141, 77.6101),
            new Location(13.0569, 77.5986),
            new Location(12.9567, 77.7010),
            new Location(12.9850, 77.5540)
    );

    private NodeLocatorBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java ... NodeLocatorBenchmark <nodes.csv> <edges.csv>");
            System.exit(1);
        }

        RoadGraph graph = new CsvRoadNetworkLoader(Path.of(args[0]), Path.of(args[1])).load();
        BruteForceNodeLocator locator = new BruteForceNodeLocator(graph);

        System.out.println("=== Node Locator Benchmark ===");
        System.out.printf("Graph nodes: %,d%n", graph.nodeCount());
        System.out.printf("Queries:     %d%n", BENCHMARK_LOCATIONS.size());
        System.out.println();

        NodeId[] selectedNodes = new NodeId[BENCHMARK_LOCATIONS.size()];
        for (int warmup = 0; warmup < WARMUP_ROUNDS; warmup++) {
            for (Location location : BENCHMARK_LOCATIONS) {
                locator.findNearest(location);
            }
        }

        long totalNanos = 0L;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = Long.MIN_VALUE;

        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            for (int i = 0; i < BENCHMARK_LOCATIONS.size(); i++) {
                Location location = BENCHMARK_LOCATIONS.get(i);
                long start = System.nanoTime();
                NodeId nodeId = locator.findNearest(location);
                long elapsed = System.nanoTime() - start;

                selectedNodes[i] = nodeId;
                totalNanos += elapsed;
                minNanos = Math.min(minNanos, elapsed);
                maxNanos = Math.max(maxNanos, elapsed);
            }
        }

        System.out.println("Results");
        for (int i = 0; i < BENCHMARK_LOCATIONS.size(); i++) {
            System.out.printf("  Query %2d: %s -> node %s%n", i + 1, BENCHMARK_LOCATIONS.get(i), selectedNodes[i]);
        }
        System.out.println();
        long lookupCount = (long) MEASURED_ROUNDS * BENCHMARK_LOCATIONS.size();
        double averageMs = totalNanos / (double) lookupCount / 1_000_000.0;
        double minMs = minNanos / 1_000_000.0;
        double maxMs = maxNanos / 1_000_000.0;
        System.out.println("Timing");
        System.out.printf("  Measured lookups: %d%n", lookupCount);
        System.out.printf("  Average:          %.3f ms%n", averageMs);
        System.out.printf("  Min:              %.3f ms%n", minMs);
        System.out.printf("  Max:              %.3f ms%n", maxMs);
    }
}
