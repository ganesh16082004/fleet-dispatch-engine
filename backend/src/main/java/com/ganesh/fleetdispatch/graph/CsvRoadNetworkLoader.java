package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads a road graph from two deterministic CSV snapshots: nodes and directed edges. */
public final class CsvRoadNetworkLoader implements RoadNetworkLoader {
    private static final String NODE_HEADER = "id,latitude,longitude";
    private static final String EDGE_HEADER = "from,to,distance_meters,travel_time_seconds";

    private final Path nodesFile;
    private final Path edgesFile;

    public CsvRoadNetworkLoader(Path nodesFile, Path edgesFile) {
        this.nodesFile = Objects.requireNonNull(nodesFile, "nodesFile");
        this.edgesFile = Objects.requireNonNull(edgesFile, "edgesFile");
    }

    @Override
    public RoadGraph load() {
        try {
            Map<NodeId, RoadNode> nodes = loadNodes();
            List<RoadEdge> edges = loadEdges();
            return new RoadGraph(nodes, edges);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalArgumentException("Unable to load road-network CSV snapshot", exception);
        }
    }

    private Map<NodeId, RoadNode> loadNodes() throws IOException {
        Map<NodeId, RoadNode> nodes = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(nodesFile)) {
            requireHeader(reader, NODE_HEADER, nodesFile);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = split(line, 3, nodesFile, lineNumber);
                NodeId id = new NodeId(Long.parseLong(fields[0].trim()));
                RoadNode previous = nodes.put(
                        id,
                        new RoadNode(
                                id,
                                new Location(
                                        Double.parseDouble(fields[1].trim()),
                                        Double.parseDouble(fields[2].trim()))));
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate node id at " + nodesFile + ":" + lineNumber);
                }
            }
        }
        return nodes;
    }

    private List<RoadEdge> loadEdges() throws IOException {
        List<RoadEdge> edges = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(edgesFile)) {
            requireHeader(reader, EDGE_HEADER, edgesFile);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = split(line, 4, edgesFile, lineNumber);
                edges.add(
                        new RoadEdge(
                                new NodeId(Long.parseLong(fields[0].trim())),
                                new NodeId(Long.parseLong(fields[1].trim())),
                                Double.parseDouble(fields[2].trim()),
                                Double.parseDouble(fields[3].trim())));
            }
        }
        return edges;
    }

    private static String[] split(String line, int expectedFields, Path file, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != expectedFields) {
            throw new IllegalArgumentException(
                    "Expected " + expectedFields + " fields at " + file + ":" + lineNumber);
        }
        return fields;
    }

    private static void requireHeader(BufferedReader reader, String expected, Path file) throws IOException {
        String header = reader.readLine();
        if (!expected.equals(header)) {
            throw new IllegalArgumentException(
                    "Invalid header in " + file + ". Expected: " + expected);
        }
    }
}
