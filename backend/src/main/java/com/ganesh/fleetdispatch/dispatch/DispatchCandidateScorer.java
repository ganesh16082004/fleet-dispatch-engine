package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

/** Scores routed candidates for dispatch selection. Lower scores are preferred. */
@FunctionalInterface
public interface DispatchCandidateScorer {
    /** Returns a score where lower values represent better candidates. */
    double score(DriverCandidate candidate, Route route);

    /**
     * Called after the order side has successfully committed an assignment.
     * Scorers that maintain state, such as workload-aware policies, may use this
     * callback to update their future ranking inputs.
     */
    default void onAssignmentCommitted(Driver driver) {
        // Stateless scorers do not need assignment lifecycle callbacks.
    }
}
