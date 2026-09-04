package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Ranks available drivers for offers using actual route travel time and distance. */
public final class OfferCandidateSelector {
    private final CandidateSelector candidateSelector;
    private final Router router;
    private final int maxCandidates;

    public OfferCandidateSelector(
            CandidateSelector candidateSelector,
            Router router,
            int maxCandidates) {
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.router = Objects.requireNonNull(router, "router");
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }
        this.maxCandidates = maxCandidates;
    }

    public List<OfferCandidate> select(Order order, double radiusMeters) {
        Objects.requireNonNull(order, "order");
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0.0) {
            throw new IllegalArgumentException("radiusMeters must be finite and non-negative");
        }

        return candidateSelector.select(order, radiusMeters, maxCandidates).stream()
                .map(candidate -> toOfferCandidate(candidate, order))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator
                        .comparingDouble(OfferCandidate::incrementalTravelTimeSeconds)
                        .thenComparingDouble(OfferCandidate::incrementalDistanceMeters)
                        .thenComparingLong(candidate -> candidate.driver().id()))
                .toList();
    }

    private java.util.Optional<OfferCandidate> toOfferCandidate(
            DriverCandidate candidate,
            Order order) {
        return router.route(candidate.driver().currentNode(), order.pickupNode())
                .map(route -> new OfferCandidate(
                        candidate.driver(),
                        route,
                        route.totalTravelTimeSeconds(),
                        route.totalDistanceMeters()));
    }
}
