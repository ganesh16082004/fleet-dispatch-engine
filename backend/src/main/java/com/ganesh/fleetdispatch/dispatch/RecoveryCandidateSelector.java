package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ranks available replacement drivers by the complete rescue route. */
public final class RecoveryCandidateSelector {
    private final DriverStateStore driverStateStore;
    private final RouteFinder routeFinder;

    public RecoveryCandidateSelector(
            DriverStateStore driverStateStore,
            RouteFinder routeFinder) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.routeFinder = Objects.requireNonNull(routeFinder, "routeFinder");
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
        return driverStateStore.getAvailableDrivers().stream()
                .filter(driver -> driver.id() != task.failedDriverId())
                .map(driver -> toCandidate(driver, task.handoffNode(), finalRoute))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingDouble(RecoveryCandidate::totalTravelTimeSeconds)
                        .thenComparingDouble(RecoveryCandidate::totalDistanceMeters)
                        .thenComparingLong(candidate -> candidate.driver().id()))
                .toList();
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
