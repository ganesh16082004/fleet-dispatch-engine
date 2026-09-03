package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dijkstra's shortest-path algorithm using travel time as the edge cost.
 * This implementation is intentionally used as the correctness baseline
 * for faster informed routing strategies.
 */
public final class DijkstraRouter implements Router {
    private static final double EPSILON = 1e-9;

    private final RoadGraph graph;

    public DijkstraRouter(RoadGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null");
        }
        this.graph = graph;
    }

    @Override
    public Route findRoute(NodeId source, NodeId target) {
        return findRoute(source, target, null);
    }

    /** Finds a route while optionally recording per-query algorithm metrics. */
    public Route findRoute(NodeId source, NodeId target, RoutingMetrics metrics) {
        requireNode(source, "source");
        requireNode(target, "target");

        if (source.equals(target)) {
            return new Route(List.of(source), 0.0, 0.0);
        }

        Map<NodeId, Double> distance = new HashMap<>();
        Map<NodeId, NodeId> previous = new HashMap<>();
        Map<NodeId, RoadEdge> previousEdge = new HashMap<>();
        Set<NodeId> settled = new HashSet<>();

        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        distance.put(source, 0.0);
        queue.add(new NodeDistance(source, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            double knownDistance = distance.getOrDefault(current.node(), Double.POSITIVE_INFINITY);
            if (current.distance() > knownDistance + EPSILON) {
                continue;
            }
            if (!settled.add(current.node())) {
                continue;
            }
            if (metrics != null) {
                metrics.recordNodeExpanded();
            }
            if (current.node().equals(target)) {
                break;
            }

            for (RoadEdge edge : graph.outgoing(current.node())) {
                if (settled.contains(edge.to())) {
                    continue;
                }

                double candidate = current.distance() + edge.travelTimeSeconds();
                double existing = distance.getOrDefault(edge.to(), Double.POSITIVE_INFINITY);
                if (candidate + EPSILON < existing) {
                    distance.put(edge.to(), candidate);
                    previous.put(edge.to(), current.node());
                    previousEdge.put(edge.to(), edge);
                    queue.add(new NodeDistance(edge.to(), candidate));
                    if (metrics != null) {
                        metrics.recordEdgeRelaxed();
                    }
                }
            }
        }

        if (!distance.containsKey(target)) {
            throw new IllegalArgumentException("No route exists from " + source + " to " + target);
        }

        List<NodeId> nodes = reconstructPath(source, target, previous);
        double totalDistance = 0.0;
        for (int i = 1; i < nodes.size(); i++) {
            totalDistance += previousEdge.get(nodes.get(i)).distanceMeters();
        }

        return new Route(nodes, distance.get(target), totalDistance);
    }

    private void requireNode(NodeId node, String name) {
        if (node == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (graph.node(node) == null) {
            throw new IllegalArgumentException(name + " does not exist in graph: " + node);
        }
    }

    private List<NodeId> reconstructPath(NodeId source, NodeId target, Map<NodeId, NodeId> previous) {
        List<NodeId> reversed = new ArrayList<>();
        NodeId cursor = target;
        reversed.add(cursor);
        while (!cursor.equals(source)) {
            cursor = previous.get(cursor);
            if (cursor == null) {
                throw new IllegalStateException("Could not reconstruct shortest path");
            }
            reversed.add(cursor);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private record NodeDistance(NodeId node, double distance) {
    }

    private static final class PriorityQueue<T> extends java.util.PriorityQueue<T> {
    }
}
