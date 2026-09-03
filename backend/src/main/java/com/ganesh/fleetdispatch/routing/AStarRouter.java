package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.domain.Location;
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
 * A* shortest-path router using straight-line geographic distance as a heuristic.
 * The heuristic is converted to a lower-bound travel time using the configured
 * maximum travel speed of the graph.
 */
public final class AStarRouter implements Router {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double EPSILON = 1e-9;

    private final RoadGraph graph;
    private final double maximumSpeedMetersPerSecond;

    public AStarRouter(RoadGraph graph, double maximumSpeedMetersPerSecond) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null");
        }
        if (!Double.isFinite(maximumSpeedMetersPerSecond) || maximumSpeedMetersPerSecond <= 0) {
            throw new IllegalArgumentException("maximumSpeedMetersPerSecond must be positive and finite");
        }
        this.graph = graph;
        this.maximumSpeedMetersPerSecond = maximumSpeedMetersPerSecond;
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

        Map<NodeId, Double> bestG = new HashMap<>();
        Map<NodeId, NodeId> previous = new HashMap<>();
        Map<NodeId, RoadEdge> previousEdge = new HashMap<>();
        Set<NodeId> settled = new HashSet<>();

        var queue = new java.util.PriorityQueue<NodeScore>(Comparator.comparingDouble(NodeScore::fScore));
        bestG.put(source, 0.0);
        queue.add(new NodeScore(source, 0.0, heuristicSeconds(source, target)));

        while (!queue.isEmpty()) {
            NodeScore current = queue.poll();
            double knownG = bestG.getOrDefault(current.node(), Double.POSITIVE_INFINITY);
            if (current.gScore() > knownG + EPSILON) {
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

                double candidateG = current.gScore() + edge.travelTimeSeconds();
                double existingG = bestG.getOrDefault(edge.to(), Double.POSITIVE_INFINITY);
                if (candidateG + EPSILON < existingG) {
                    bestG.put(edge.to(), candidateG);
                    previous.put(edge.to(), current.node());
                    previousEdge.put(edge.to(), edge);
                    double fScore = candidateG + heuristicSeconds(edge.to(), target);
                    queue.add(new NodeScore(edge.to(), candidateG, fScore));
                    if (metrics != null) {
                        metrics.recordEdgeRelaxed();
                    }
                }
            }
        }

        if (!bestG.containsKey(target)) {
            throw new IllegalArgumentException("No route exists from " + source + " to " + target);
        }

        List<NodeId> nodes = reconstructPath(source, target, previous);
        double totalDistance = 0.0;
        for (int i = 1; i < nodes.size(); i++) {
            totalDistance += previousEdge.get(nodes.get(i)).distanceMeters();
        }

        return new Route(nodes, bestG.get(target), totalDistance);
    }

    private double heuristicSeconds(NodeId from, NodeId target) {
        Location a = graph.node(from).location();
        Location b = graph.node(target).location();
        return haversineMeters(a, b) / maximumSpeedMetersPerSecond;
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

    static double haversineMeters(Location a, Location b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.longitude() - a.longitude());

        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLon = Math.sin(deltaLon / 2.0);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1.0, h)));
    }

    private record NodeScore(NodeId node, double gScore, double fScore) {
    }
}
