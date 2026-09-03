package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;
import java.util.Objects;

/**
 * Baseline nearest-node lookup that scans every node in the graph.
 *
 * <p>This implementation intentionally favors correctness and simplicity over
 * lookup performance. A spatial index can replace it later behind the
 * {@link NodeLocator} interface and be benchmarked against this baseline.</p>
 */
public final class BruteForceNodeLocator implements NodeLocator {
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final RoadGraph graph;

    public BruteForceNodeLocator(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @Override
    public NodeId findNearest(Location location) {
        Objects.requireNonNull(location, "location");

        NodeId nearestId = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (RoadNode node : graph.nodes().values()) {
            double distance = haversineMeters(location, node.location());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestId = node.id();
            }
        }

        if (nearestId == null) {
            throw new IllegalStateException("Cannot locate a node in an empty road graph");
        }
        return nearestId;
    }

    private static double haversineMeters(Location a, Location b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }
}
