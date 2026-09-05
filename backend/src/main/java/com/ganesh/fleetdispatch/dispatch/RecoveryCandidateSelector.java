package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ranks available replacement drivers by the complete rescue route. */
public final class RecoveryCandidateSelector {
    private static final double RECOVERY_SEARCH_RADIUS_METERS = 8_000.0;
    private static final int MAX_RECOVERY_CANDIDATES = 10;

    private final DriverStateStore driverStateStore;
    private final RouteFinder routeFinder;
    private final RoadGraph roadGraph;

    public RecoveryCandidateSelector(
            DriverStateStore driverStateStore,
            RouteFinder routeFinder) {
        this(driverStateStore, routeFinder, null);
    }

    public RecoveryCandidateSelector(
            DriverStateStore driverStateStore,
            RouteFinder routeFinder,
            RoadGraph roadGraph) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.routeFinder = Objects.requireNonNull(routeFinder, "routeFinder");
        this.roadGraph = roadGraph;
    }

    public List<RecoveryCandidate> select(
            DriverRecoveryTask task,
            Order order) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(order, "order");

        Optional<Route> handoffToDropoff = routeFinder.findRoute(
                task.handoffNode(),
                order.dropoffNode());
        if (handoffToDropoff.isEmpty()) {
            return List.of();
        }

        Route finalRoute = handoffToDropoff.get();
        return candidateDrivers(task)
                .stream()
                .filter(driver -> driver.id() != task.failedDriverId())
                .map(driver -> toCandidate(driver, task.handoffNode(), finalRoute))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingDouble(RecoveryCandidate::totalTravelTimeSeconds)
                        .thenComparingDouble(RecoveryCandidate::totalDistanceMeters)
                        .thenComparingLong(candidate -> candidate.driver().id()))
                .toList();
    }

    private List<Driver> candidateDrivers(DriverRecoveryTask task) {
        if (roadGraph == null) {
            return driverStateStore.getAvailableDrivers();
        }

        var handoffNode = roadGraph.node(task.handoffNode());
        if (handoffNode == null) {
            return List.of();
        }

        Location handoffLocation = handoffNode.location();
        return driverStateStore.getAvailableDriversNear(
                handoffLocation,
                RECOVERY_SEARCH_RADIUS_METERS,
                MAX_RECOVERY_CANDIDATES);
    }

    private Optional<RecoveryCandidate> toCandidate(
            Driver driver,
            NodeId handoffNode,
            Route handoffToDropoffRoute) {
        return routeFinder.findRoute(driver.currentNode(), handoffNode)
                .map(route -> new RecoveryCandidate(driver, route, handoffToDropoffRoute));
    }

    @FunctionalInterface
    public interface RouteFinder {
        Optional<Route> findRoute(NodeId source, NodeId target);
    }
}
