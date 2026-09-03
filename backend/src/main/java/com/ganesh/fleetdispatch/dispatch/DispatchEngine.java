package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts nearby-driver candidates into an actual dispatch decision.
 *
 * <p>Candidate discovery is deliberately separated from dispatch so the
 * search radius and candidate bound can evolve independently from the
 * assignment policy.</p>
 */
public final class DispatchEngine {
    private final CandidateSelector candidateSelector;
    private final DriverStateStore driverStateStore;
    private final Router router;
    private final double searchRadiusMeters;
    private final int maxCandidates;

    public DispatchEngine(
            CandidateSelector candidateSelector,
            DriverStateStore driverStateStore,
            Router router,
            double searchRadiusMeters,
            int maxCandidates) {
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.router = Objects.requireNonNull(router, "router");

        if (!Double.isFinite(searchRadiusMeters) || searchRadiusMeters < 0) {
            throw new IllegalArgumentException("searchRadiusMeters must be finite and non-negative");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }

        this.searchRadiusMeters = searchRadiusMeters;
        this.maxCandidates = maxCandidates;
    }

    /**
     * Attempts to assign an order to an available driver.
     *
     * <p>Candidates are first ranked by actual road travel time to the pickup
     * node, then by route distance and driver id for deterministic tie-breaking.
     * Reservation is conditional on the driver's node remaining unchanged so a
     * stale routing result cannot claim a driver who moved concurrently.</p>
     */
    public Optional<DispatchAssignment> dispatch(Order order) {
        Objects.requireNonNull(order, "order");
        if (order.status() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("Only CREATED orders can be dispatched: " + order.id());
        }

        List<RoutedCandidate> routedCandidates = candidateSelector
                .select(order, searchRadiusMeters, maxCandidates)
                .stream()
                .map(candidate -> routeCandidate(candidate, order))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingDouble((RoutedCandidate candidate) -> candidate.route().totalTravelTimeSeconds())
                        .thenComparingDouble(candidate -> candidate.route().totalDistanceMeters())
                        .thenComparingLong(candidate -> candidate.candidate().driver().id()))
                .toList();

        for (RoutedCandidate candidate : routedCandidates) {
            DriverCandidate driverCandidate = candidate.candidate();
            Driver driver = driverCandidate.driver();

            if (!driverStateStore.reserveDriver(driver.id(), driver.currentNode())) {
                continue;
            }

            return Optional.of(new DispatchAssignment(
                    order.id(),
                    driver.id(),
                    candidate.route()));
        }

        return Optional.empty();
    }

    private Optional<RoutedCandidate> routeCandidate(DriverCandidate candidate, Order order) {
        try {
            Route route = router.findRoute(candidate.driverNode(), order.pickupNode());
            return Optional.of(new RoutedCandidate(candidate, route));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private record RoutedCandidate(DriverCandidate candidate, Route route) {
    }
}
