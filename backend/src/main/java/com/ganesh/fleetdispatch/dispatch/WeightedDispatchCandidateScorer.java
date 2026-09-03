package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/**
 * Scores candidates using a configurable weighted combination of route travel time and distance.
 * Lower scores are preferred.
 */
public final class WeightedDispatchCandidateScorer implements DispatchCandidateScorer {
    private final double travelTimeWeight;
    private final double distanceWeight;

    public WeightedDispatchCandidateScorer(double travelTimeWeight, double distanceWeight) {
        if (!Double.isFinite(travelTimeWeight) || travelTimeWeight < 0) {
            throw new IllegalArgumentException("travelTimeWeight must be finite and non-negative");
        }
        if (!Double.isFinite(distanceWeight) || distanceWeight < 0) {
            throw new IllegalArgumentException("distanceWeight must be finite and non-negative");
        }
        if (travelTimeWeight == 0.0 && distanceWeight == 0.0) {
            throw new IllegalArgumentException("At least one scoring weight must be positive");
        }
        this.travelTimeWeight = travelTimeWeight;
        this.distanceWeight = distanceWeight;
    }

    @Override
    public double score(DriverCandidate candidate, Route route) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(route, "route");
        return travelTimeWeight * route.totalTravelTimeSeconds()
                + distanceWeight * route.totalDistanceMeters();
    }
}
