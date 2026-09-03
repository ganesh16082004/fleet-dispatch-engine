package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

/** Prefers the candidate with the shortest road travel time to pickup. */
public final class TravelTimeDispatchCandidateScorer implements DispatchCandidateScorer {
    @Override
    public double score(DriverCandidate candidate, Route route) {
        return route.totalTravelTimeSeconds();
    }
}
