package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Finds busy drivers whose existing routes have capacity for consolidation. */
public final class RouteCandidateSelector {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final DriverStateStore driverStateStore;
    private final DriverRouteStore routeStore;
    private final RoadGraph roadGraph;

    public RouteCandidateSelector(
            DriverStateStore driverStateStore,
            DriverRouteStore routeStore,
            RoadGraph roadGraph) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.routeStore = Objects.requireNonNull(routeStore, "routeStore");
        this.roadGraph = Objects.requireNonNull(roadGraph, "roadGraph");
    }

    public List<RouteDriverCandidate> select(
            Order order,
            double radiusMeters,
            int maxCandidates) {
        Objects.requireNonNull(order, "order");
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0.0) {
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
        return routeStore.getActiveDriverIds().stream()
                .map(routeStore::getPlan)
                .flatMap(java.util.Optional::stream)
                .filter(plan -> plan.activeDeliveryCount() < DriverRoutePlan.MAX_ACTIVE_DELIVERIES)
                .map(plan -> new DriverPlanHolder(plan))
                .map(holder -> driverStateStore.getDriver(holder.plan().activeOrders().get(0).id()))
                .findAny()
                .map(ignored -> selectUsingPlans(pickup, radiusMeters, maxCandidates))
                .orElseGet(() -> selectUsingPlans(pickup, radiusMeters, maxCandidates));
    }

    private List<RouteDriverCandidate> selectUsingPlans(
            Location pickup,
            double radiusMeters,
            int maxCandidates) {
        return routeStore.getActiveDriverIds().stream()
                .map(driverId -> {
                    DriverRoutePlan plan = routeStore.getPlan(driverId).orElse(null);
                    if (plan == null || plan.activeDeliveryCount() >= DriverRoutePlan.MAX_ACTIVE_DELIVERIES) {
                        return null;
                    }
                    Driver driver = driverStateStore.getDriver(driverId).orElse(null);
                    if (driver == null) {
                        return null;
                    }
                    RoadNode driverNode = roadGraph.node(driver.currentNode());
                    if (driverNode == null) {
                        return null;
                    }
                    double distance = haversineMeters(pickup, driverNode.location());
                    return distance <= radiusMeters
                            ? new RouteDriverCandidate(driver, plan, distance)
                            : null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingDouble(RouteDriverCandidate::distanceMeters)
                        .thenComparingLong(candidate -> candidate.driver().id()))
                .limit(maxCandidates)
                .toList();
    }

    private static double haversineMeters(Location a, Location b) {
        double phi1 = Math.toRadians(a.latitude());
        double phi2 = Math.toRadians(b.latitude());
        double dPhi = Math.toRadians(b.latitude() - a.latitude());
        double dLambda = Math.toRadians(b.longitude() - a.longitude());
        double sinPhi = Math.sin(dPhi / 2.0);
        double sinLambda = Math.sin(dLambda / 2.0);
        double h = sinPhi * sinPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
        return EARTH_RADIUS_METERS * 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    }

    private record DriverPlanHolder(DriverRoutePlan plan) {
    }

    public record RouteDriverCandidate(
            Driver driver,
            DriverRoutePlan plan,
            double distanceMeters) {
        public RouteDriverCandidate {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(plan, "plan");
            if (!Double.isFinite(distanceMeters) || distanceMeters < 0.0) {
                throw new IllegalArgumentException("distanceMeters must be finite and non-negative");
            }
        }
    }
}
