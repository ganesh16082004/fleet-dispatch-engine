package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.Objects;

/** A dispatch candidate with its straight-line distance to the order pickup node. */
public record DriverCandidate(
        Driver driver,
        double distanceMeters
) {
    public DriverCandidate {
        Objects.requireNonNull(driver, "driver must not be null");

        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            throw new IllegalArgumentException("distanceMeters must be finite and non-negative");
        }
    }

    public NodeId driverNode() {
        return driver.currentNode();
    }
}
