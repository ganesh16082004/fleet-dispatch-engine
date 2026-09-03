package com.ganesh.fleetdispatch.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvRoadNetworkLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsNodesAndDirectedEdges() throws IOException {
        Path nodes = tempDir.resolve("nodes.csv");
        Path edges = tempDir.resolve("edges.csv");

        Files.writeString(nodes, """
                id,latitude,longitude
                1,12.9716,77.5946
                2,12.9720,77.5950
                3,12.9725,77.5960
                """);
        Files.writeString(edges, """
                from,to,distance_meters,travel_time_seconds
                1,2,60.0,6.0
                2,3,90.0,9.0
                """);

        RoadGraph graph = new CsvRoadNetworkLoader(nodes, edges).load();

        assertEquals(3, graph.nodeCount());
        assertEquals(2, graph.edgeCount());
        assertEquals(new NodeId(2), graph.outgoing(new NodeId(1)).getFirst().to());
        assertEquals(60.0, graph.outgoing(new NodeId(1)).getFirst().distanceMeters());
        assertEquals(6.0, graph.outgoing(new NodeId(1)).getFirst().travelTimeSeconds());
    }

    @Test
    void rejectsMalformedRow() throws IOException {
        Path nodes = tempDir.resolve("nodes.csv");
        Path edges = tempDir.resolve("edges.csv");

        Files.writeString(nodes, "id,latitude,longitude\n1,12.9716\n");
        Files.writeString(edges, "from,to,distance_meters,travel_time_seconds\n");

        assertThrows(IllegalArgumentException.class, () -> new CsvRoadNetworkLoader(nodes, edges).load());
    }

    @Test
    void rejectsEdgeThatReferencesUnknownNode() throws IOException {
        Path nodes = tempDir.resolve("nodes.csv");
        Path edges = tempDir.resolve("edges.csv");

        Files.writeString(nodes, "id,latitude,longitude\n1,12.9716,77.5946\n");
        Files.writeString(edges, "from,to,distance_meters,travel_time_seconds\n1,99,60.0,6.0\n");

        assertThrows(IllegalArgumentException.class, () -> new CsvRoadNetworkLoader(nodes, edges).load());
    }
}
