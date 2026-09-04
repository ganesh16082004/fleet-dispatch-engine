package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects a bounded set of available drivers near an order pickup location. */
public final class CandidateSelector {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final DriverStateStore driverStateStore;
    private final RoadGraph roadGraph;

    public CandidateSelector(DriverStateStore driverStateStore, RoadGraph roadGraph) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.roadGraph = Objects.requireNonNull(roadGraph, "roadGraph");
    }

    public List<DriverCandidate> select(Order order, double radiusMeters, int maxCandidates) {
        Objects.requireNonNull(order, "order");

        if (!Double.isFinite(radiusMeters) || radiusMeters < 0) {
            throw new IllegalArgumentException("radiusMeters must be finite and non-negative");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }

        RoadNode pickupNode = roadGraph.node(order.pickupNode());
        if (pickupNode == null) {
            throw new IllegalArgumentException("Pickup node does not exist: " + order.pickupNode());
        }

        Location pickup = pickupNode.location();

        return driverStateStore.getAvailableDriversNear(pickup, radiusMeters, maxCandidates).stream()
                .map(driver -> {
                    RoadNode driverNode = roadGraph.node(driver.currentNode());
                    if (driverNode == null) {
                        return null;
                    }

                    double distance = haversineMeters(
                            pickup.latitude(), pickup.longitude(),
                            driverNode.location().latitude(), driverNode.location().longitude());

                    return new DriverCandidate(driver, distance);
                })
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                .sorted(Comparator
                        .comparingDouble(DriverCandidate::distanceMeters)
                        .thenComparingLong(candidate -> candidate.driver().id()))
                .limit(maxCandidates)
                .toList();
    }

    RoadGraph roadGraph() {
        return roadGraph;
    }

    private static double haversineMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2.0) * Math.sin(dPhi / 2.0)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(dLambda / 2.0) * Math.sin(dLambda / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
