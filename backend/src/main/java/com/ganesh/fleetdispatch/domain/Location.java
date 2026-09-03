package com.ganesh.fleetdispatch.domain;

/** Immutable geographic coordinate used throughout the simulation. */
public record Location(double latitude, double longitude) {
    public Location {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be finite and in [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be finite and in [-180, 180]");
        }
    }
}
