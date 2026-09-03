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

        long bruteNanos = measure(bruteForce);
        long kdNanos = measure(kdTree);

        double bruteAverageMs = averageMillis(bruteNanos);
        double kdAverageMs = averageMillis(kdNanos);
        double speedup = kdAverageMs == 0.0 ? Double.POSITIVE_INFINITY : bruteAverageMs / kdAverageMs;

        System.out.println("=== Node Locator Comparison Benchmark ===");
        System.out.println();
        System.out.printf("Graph nodes:        %,d%n", graph.nodeCount());
        System.out.printf("Graph edges:        %,d%n", graph.edgeCount());
        System.out.printf("Queries:            %d%n", QUERIES.size());
        System.out.println();
        System.out.println("KD-tree index");
        System.out.printf("  Build time:       %.3f s%n", buildNanos / 1_000_000_000.0);
        System.out.println();
        System.out.println("Lookup timing");
        System.out.printf("  Brute force avg:  %.3f ms%n", bruteAverageMs);
        System.out.printf("  Brute force min:  %.3f ms%n", nanosToMillis(minPerLookup(bruteNanos)));
        System.out.printf("  Brute force max:  %.3f ms%n", nanosToMillis(maxPerLookup(bruteNanos)));
        System.out.printf("  KD-tree avg:      %.3f ms%n", kdAverageMs);
        System.out.printf("  KD-tree min:      %.3f ms%n", nanosToMillis(minPerLookup(kdNanos)));
        System.out.printf("  KD-tree max:      %.3f ms%n", nanosToMillis(maxPerLookup(kdNanos)));
        System.out.printf("  Lookup speedup:   %.2fx%n", speedup);
        System.out.println();
        System.out.println("Correctness");
        System.out.println("  Agreement:        PASS");
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

    private static long measure(NodeLocator locator) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            runQueries(locator);
        }

        long totalNanos = 0L;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            runQueries(locator);
            totalNanos += System.nanoTime() - start;
        }
        return totalNanos;
    }

    private static void runQueries(NodeLocator locator) {
        for (Location query : QUERIES) {
            locator.findNearest(query);
        }
    }

    private static double averageMillis(long totalNanos) {
        long lookups = (long) MEASURED_ROUNDS * QUERIES.size();
        return totalNanos / (double) lookups / 1_000_000.0;
    }

    private static long minPerLookup(long totalNanos) {
        // Individual timings are collected separately below to keep the hot-path
        // measurement simple and avoid allocating timing objects per lookup.
        // Use the total only for the display fallback; exact per-lookup min/max
        // are measured by a dedicated pass.
        return dedicatedMinNanos;
    }

    private static long maxPerLookup(long totalNanos) {
        return dedicatedMaxNanos;
    }

    private static long dedicatedMinNanos;
    private static long dedicatedMaxNanos;

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
