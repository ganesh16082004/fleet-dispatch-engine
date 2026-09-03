package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;

import java.nio.file.Path;
import java.util.List;

/**
 * Compares the brute-force and KD-tree nearest-node implementations on a real graph.
 *
 * <p>The benchmark keeps graph loading outside the measured lookup section. KD-tree
 * construction is reported separately because it is a one-time indexing cost.</p>
 */
public final class NodeLocatorComparisonBenchmark {
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 20;

    private static final List<Location> QUERIES = List.of(
            new Location(12.9716, 77.5946),
            new Location(12.9352, 77.6245),
            new Location(13.0358, 77.5970),
            new Location(12.9784, 77.6408),
            new Location(12.9279, 77.6271),
            new Location(13.0068, 77.5813),
            new Location(12.9141, 77.6101),
            new Location(13.0569, 77.5986),
            new Location(12.9567, 77.7010),
            new Location(12.9850, 77.5540));

    private NodeLocatorComparisonBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: NodeLocatorComparisonBenchmark <nodes.csv> <edges.csv>");
        }

        Path nodesFile = Path.of(args[0]);
        Path edgesFile = Path.of(args[1]);

        RoadGraph graph = new CsvRoadNetworkLoader(nodesFile, edgesFile).load();
        NodeLocator bruteForce = new BruteForceNodeLocator(graph);

        long buildStart = System.nanoTime();
        NodeLocator kdTree = new KdTreeNodeLocator(graph);
        long buildNanos = System.nanoTime() - buildStart;

        verifyAgreement(bruteForce, kdTree);

        TimingStats bruteStats = measure(bruteForce);
        TimingStats kdStats = measure(kdTree);

        double speedup = kdStats.averageMillis() == 0.0
                ? Double.POSITIVE_INFINITY
                : bruteStats.averageMillis() / kdStats.averageMillis();

        System.out.println("=== Node Locator Comparison Benchmark ===");
        System.out.println();
        System.out.printf("Graph nodes:        %,d%n", graph.nodeCount());
        System.out.printf("Graph edges:        %,d%n", graph.edgeCount());
        System.out.printf("Queries:            %d%n", QUERIES.size());
        System.out.printf("Measured lookups:   %d%n", bruteStats.lookupCount());
        System.out.println();
        System.out.println("KD-tree index");
        System.out.printf("  Build time:       %.3f s%n", buildNanos / 1_000_000_000.0);
        System.out.println();
        System.out.println("Lookup timing");
        printStats("Brute force", bruteStats);
        printStats("KD-tree", kdStats);
        System.out.printf("  Lookup speedup:   %.2fx%n", speedup);
        System.out.println();
        System.out.println("Correctness");
        System.out.println("  Agreement:        PASS");
    }

    private static void printStats(String name, TimingStats stats) {
        System.out.printf("  %-18s avg: %.3f ms%n", name, stats.averageMillis());
        System.out.printf("  %-18s min: %.3f ms%n", "", stats.minMillis());
        System.out.printf("  %-18s max: %.3f ms%n", "", stats.maxMillis());
    }

    private static void verifyAgreement(NodeLocator baseline, NodeLocator optimized) {
        for (Location query : QUERIES) {
            NodeId expected = baseline.findNearest(query);
            NodeId actual = optimized.findNearest(query);
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "Nearest-node mismatch for " + query + ": expected " + expected + ", got " + actual);
            }
        }
    }

    private static TimingStats measure(NodeLocator locator) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            runQueries(locator);
        }

        long totalNanos = 0L;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = Long.MIN_VALUE;
        int lookupCount = 0;

        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            for (Location query : QUERIES) {
                long start = System.nanoTime();
                locator.findNearest(query);
                long elapsed = System.nanoTime() - start;

                totalNanos += elapsed;
                minNanos = Math.min(minNanos, elapsed);
                maxNanos = Math.max(maxNanos, elapsed);
                lookupCount++;
            }
        }

        return new TimingStats(totalNanos, minNanos, maxNanos, lookupCount);
    }

    private static void runQueries(NodeLocator locator) {
        for (Location query : QUERIES) {
            locator.findNearest(query);
        }
    }

    private record TimingStats(long totalNanos, long minNanos, long maxNanos, int lookupCount) {
        double averageMillis() {
            return totalNanos / (double) lookupCount / 1_000_000.0;
        }

        double minMillis() {
            return minNanos / 1_000_000.0;
        }

        double maxMillis() {
            return maxNanos / 1_000_000.0;
        }
    }
}
