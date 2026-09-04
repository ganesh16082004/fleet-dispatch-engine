package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Scores candidates using travel cost plus a configurable workload penalty.
 * Lower scores are preferred. Workload is the number of assignments committed
 * to each driver since the scorer was created.
 */
public final class WorkloadAwareDispatchCandidateScorer implements DispatchCandidateScorer {
    private final double travelTimeWeight;
    private final double distanceWeight;
    private final double assignmentWeight;
    private final ConcurrentMap<Long, Long> committedAssignments = new ConcurrentHashMap<>();

    public WorkloadAwareDispatchCandidateScorer(
            double travelTimeWeight,
            double distanceWeight,
            double assignmentWeight) {
        validateWeight(travelTimeWeight, "travelTimeWeight");
        validateWeight(distanceWeight, "distanceWeight");
        validateWeight(assignmentWeight, "assignmentWeight");

        if (travelTimeWeight == 0.0 && distanceWeight == 0.0 && assignmentWeight == 0.0) {
            throw new IllegalArgumentException("At least one scoring weight must be positive");
        }
        this.travelTimeWeight = travelTimeWeight;
        this.distanceWeight = distanceWeight;
        this.assignmentWeight = assignmentWeight;
    }

    @Override
    public double score(DriverCandidate candidate, Route route) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(route, "route");

        long assignmentCount = committedAssignments.getOrDefault(candidate.driver().id(), 0L);
        return travelTimeWeight * route.totalTravelTimeSeconds()
                + distanceWeight * route.totalDistanceMeters()
                + assignmentWeight * assignmentCount;
    }

    @Override
    public void onAssignmentCommitted(Driver driver) {
        Objects.requireNonNull(driver, "driver");
        committedAssignments.merge(driver.id(), 1L, Long::sum);
    }

    /** Returns the number of successfully committed assignments for a driver. */
    public long assignmentCount(long driverId) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        return committedAssignments.getOrDefault(driverId, 0L);
    }

    private static void validateWeight(double weight, String name) {
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
