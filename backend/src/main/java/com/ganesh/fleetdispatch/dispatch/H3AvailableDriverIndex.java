package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.uber.h3core.H3Core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * H3-backed spatial index for available drivers.
 * H3 is used only for coarse candidate discovery; exact distance filtering
 * remains in the dispatch store so correctness does not depend on cell shape.
 */
final class H3AvailableDriverIndex {
    private static final int RESOLUTION = 9;
    private static final double AVERAGE_EDGE_METERS = 200.786148;
    private static final double SAFETY_FACTOR = 1.5;

    private final RoadGraph roadGraph;
    private final H3Core h3;
    private final ConcurrentHashMap<Long, Set<Long>> availableByCell = new ConcurrentHashMap<>();

    H3AvailableDriverIndex(RoadGraph roadGraph) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "roadGraph");
        try {
            this.h3 = H3Core.newInstance();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize H3 native library", exception);
        }
    }

    void add(Driver driver) {
        Location location = locationOf(driver.currentNode());
        if (location == null) {
            return;
        }
        long cell = h3.latLngToCell(location.latitude(), location.longitude(), RESOLUTION);
        availableByCell
                .computeIfAbsent(cell, ignored -> ConcurrentHashMap.newKeySet())
                .add(driver.id());
    }

    void remove(Driver driver) {
        Location location = locationOf(driver.currentNode());
        if (location == null) {
            return;
        }
        long cell = h3.latLngToCell(location.latitude(), location.longitude(), RESOLUTION);
        Set<Long> ids = availableByCell.get(cell);
        if (ids != null) {
            ids.remove(driver.id());
            if (ids.isEmpty()) {
                availableByCell.remove(cell, ids);
            }
        }
    }

    List<Driver> query(
            Location location,
            double radiusMeters,
            int maxCandidates,
            Function<Long, Driver> driverLookup,
            Comparator<DriverDistance> ordering) {
        long origin = h3.latLngToCell(location.latitude(), location.longitude(), RESOLUTION);
        int ring = Math.max(0, (int) Math.ceil(
                (radiusMeters * SAFETY_FACTOR) / AVERAGE_EDGE_METERS));

        PriorityQueue<DriverDistance> topK = new PriorityQueue<>(
                Math.max(1, maxCandidates),
                ordering.reversed());

        for (long cell : h3.gridDisk(origin, ring)) {
            Set<Long> ids = availableByCell.get(cell);
            if (ids == null) {
                continue;
            }
            for (Long id : ids) {
                Driver driver = driverLookup.apply(id);
                if (driver == null || driver.status() != DriverStatus.AVAILABLE) {
                    continue;
                }
                Location driverLocation = locationOf(driver.currentNode());
                if (driverLocation == null) {
                    continue;
                }
                double h = haversineH(location, driverLocation);
                DriverDistance candidate = new DriverDistance(driver, h);
                if (topK.size() < maxCandidates) {
                    topK.offer(candidate);
                } else if (ordering.compare(candidate, topK.peek()) < 0) {
                    topK.poll();
                    topK.offer(candidate);
                }
            }
        }

        List<DriverDistance> result = new ArrayList<>(topK);
        result.sort(ordering);
        return result.stream().map(DriverDistance::driver).toList();
    }

    private Location locationOf(NodeId nodeId) {
        var node = roadGraph.node(nodeId);
        return node == null ? null : node.location();
    }

    private static double haversineH(Location a, Location b) {
        double phi1 = Math.toRadians(a.latitude());
        double phi2 = Math.toRadians(b.latitude());
        double dPhi = Math.toRadians(b.latitude() - a.latitude());
        double dLambda = Math.toRadians(b.longitude() - a.longitude());
        double sinPhi = Math.sin(dPhi / 2.0);
        double sinLambda = Math.sin(dLambda / 2.0);
        return sinPhi * sinPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
    }

    record DriverDistance(Driver driver, double distanceKey) {
    }
}
