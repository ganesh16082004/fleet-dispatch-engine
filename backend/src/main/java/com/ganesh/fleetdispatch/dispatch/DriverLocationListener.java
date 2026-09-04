package com.ganesh.fleetdispatch.dispatch;

/** Receives accepted live driver location updates. */
@FunctionalInterface
public interface DriverLocationListener {
    void onLocationUpdate(DriverLocationUpdate update);
}
