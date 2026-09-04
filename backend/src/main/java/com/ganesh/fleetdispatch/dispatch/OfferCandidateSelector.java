package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ranks available drivers for offers using actual route travel time and distance. */
public final class OfferCandidateSelector {
    private final CandidateSelector candidateSelector;
    private final RouteFinder routeFinder;
    private final int maxCandidates;

    public OfferCandidateSelector(
            CandidateSelector candidateSelector,
            RouteFinder routeFinder,
            int maxCandidates) {
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.routeFinder = Objects.requireNonNull(routeFinder, "routeFinder");
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
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingDouble(OfferCandidate::incrementalTravelTimeSeconds)
                        .thenComparingDouble(OfferCandidate::incrementalDistanceMeters)
                        .thenComparingLong(candidate -> candidate.driver().id()))
                .toList();
    }

    private Optional<OfferCandidate> toOfferCandidate(
            DriverCandidate candidate,
            Order order) {
        return routeFinder.findRoute(candidate.driver().currentNode(), order.pickupNode())
                .map(route -> new OfferCandidate(
                        candidate.driver(),
                        route,
                        route.totalTravelTimeSeconds(),
                        route.totalDistanceMeters()));
    }

    @FunctionalInterface
    public interface RouteFinder {
        Optional<Route> findRoute(NodeId source, NodeId target);
    }
}
