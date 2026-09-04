package com.ganesh.fleetdispatch.dispatch;

/** Result of a driver liveness check that triggers recovery/reassignment work. */
public record DriverFailureDetection(
        long driverId,
        int pickedUpOrdersQueued,
        int assignedOrdersReassigned
) {
    public DriverFailureDetection {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (pickedUpOrdersQueued < 0 || assignedOrdersReassigned < 0) {
            throw new IllegalArgumentException("recovery counts must be non-negative");
        }
    }
}
