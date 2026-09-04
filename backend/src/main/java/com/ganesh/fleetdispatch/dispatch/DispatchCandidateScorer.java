package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

/** Scores routed candidates for dispatch selection. Lower scores are preferred. */
@FunctionalInterface
public interface DispatchCandidateScorer {
    /** Returns a score where lower values represent better candidates. */
    double score(DriverCandidate candidate, Route route);
}
