package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Comparator;

/** Scores routed candidates for dispatch selection. Lower scores are preferred. */
@FunctionalInterface
public interface DispatchCandidateScorer {
    /** Returns a score where lower values represent better candidates. */
    double score(DriverCandidate candidate, Route route);

    /** Deterministic tie-breaking used after the primary score. */
    default Comparator<RoutedCandidateView> comparator() {
        return Comparator
                .comparingDouble((RoutedCandidateView value) -> score(value.candidate(), value.route()))
                .thenComparingDouble(value -> value.route().totalDistanceMeters())
                .thenComparingLong(value -> value.candidate().driver().id());
    }

    /** Minimal view needed by the scorer comparator; implemented by dispatch candidates. */
    interface RoutedCandidateView {
        DriverCandidate candidate();
        Route route();
    }
}
