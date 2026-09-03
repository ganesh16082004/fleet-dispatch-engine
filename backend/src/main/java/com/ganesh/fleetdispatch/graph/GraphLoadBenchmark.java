package com.ganesh.fleetdispatch.graph;

import java.nio.file.Path;

/** Measures the cost of materializing a CSV road snapshot into the in-memory RoadGraph. */
public final class GraphLoadBenchmark {
    private GraphLoadBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: GraphLoadBenchmark <nodes.csv> <edges.csv>");
            System.exit(1);
        }

        Path nodesFile = Path.of(args[0]);
        Path edgesFile = Path.of(args[1]);
        CsvRoadNetworkLoader loader = new CsvRoadNetworkLoader(nodesFile, edgesFile);

        forceGc();
        long heapBefore = usedHeapBytes();
        long startNanos = System.nanoTime();
        RoadGraph graph = loader.load();
        long elapsedNanos = System.nanoTime() - startNanos;
        forceGc();
        long heapAfter = usedHeapBytes();

        System.out.println("=== Fleet Dispatch Graph Load Benchmark ===");
        System.out.printf("Nodes:              %,d%n", graph.nodeCount());
        System.out.printf("Edges:              %,d%n", graph.edgeCount());
        System.out.printf("Load time:          %.3f s%n", elapsedNanos / 1_000_000_000.0);
        System.out.printf("Used heap before:   %.2f MB%n", toMegabytes(heapBefore));
        System.out.printf("Used heap after:    %.2f MB%n", toMegabytes(heapAfter));
        System.out.printf("Approx heap delta:  %.2f MB%n", toMegabytes(heapAfter - heapBefore));

        long graphElements = (long) graph.nodeCount() + graph.edgeCount();
        double bytesPerElement = graphElements == 0
                ? 0.0
                : (double) (heapAfter - heapBefore) / graphElements;
        System.out.printf("Approx bytes/element: %.1f%n", bytesPerElement);

        if (graph.nodeCount() == 0 || graph.edgeCount() == 0) {
            throw new IllegalStateException("Loaded graph is empty");
        }
    }

    private static void forceGc() {
        for (int i = 0; i < 2; i++) {
            System.gc();
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double toMegabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
