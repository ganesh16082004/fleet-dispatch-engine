package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DijkstraRouterTest {

    private static final NodeId A = new NodeId(1);
    private static final NodeId B = new NodeId(2);
    private static final NodeId C = new NodeId(3);
    private static final NodeId D = new NodeId(4);

    @Test
    void choosesLowestTravelTimePath() {
        RoadGraph graph = graph(
                new RoadEdge(A, B, 100, 10),
                new RoadEdge(A, C, 100, 2),
                new RoadEdge(C, B, 100, 2),
                new RoadEdge(B, D, 100, 3),
                new RoadEdge(C, D, 100, 20)
        );

        Route route = new DijkstraRouter(graph).findRoute(A, D);

        assertEquals(List.of(A, C, B, D), route.nodes());
        assertEquals(7.0, route.totalTravelTimeSeconds());
        assertEquals(300.0, route.totalDistanceMeters());
    }

    @Test
    void sourceEqualsTargetReturnsZeroCostRoute() {
        RoadGraph graph = graph(new RoadEdge(A, B, 100, 10));

        Route route = new DijkstraRouter(graph).findRoute(A, A);

        assertEquals(List.of(A), route.nodes());
        assertEquals(0.0, route.totalTravelTimeSeconds());
        assertEquals(0.0, route.totalDistanceMeters());
    }

    @Test
    void unreachableTargetIsRejected() {
        RoadGraph graph = graph(new RoadEdge(A, B, 100, 10));

        assertThrows(IllegalArgumentException.class,
                () -> new DijkstraRouter(graph).findRoute(A, D));
    }

    @Test
    void unknownSourceIsRejected() {
        RoadGraph graph = graph(new RoadEdge(A, B, 100, 10));

        assertThrows(IllegalArgumentException.class,
                () -> new DijkstraRouter(graph).findRoute(D, B));
    }

    private RoadGraph graph(RoadEdge... edges) {
        Map<NodeId, RoadNode> nodes = Map.of(
                A, node(A, 12.9716, 77.5946),
                B, node(B, 12.9720, 77.5950),
                C, node(C, 12.9730, 77.5960),
                D, node(D, 12.9740, 77.5970)
        );
        return new RoadGraph(nodes, List.of(edges));
    }

    private RoadNode node(NodeId id, double lat, double lon) {
        return new RoadNode(id, new Location(lat, lon));
    }
}
