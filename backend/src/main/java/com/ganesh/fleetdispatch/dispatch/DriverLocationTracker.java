package com.ganesh.fleetdispatch.dispatch;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Applies live driver location updates ordered by per-driver sequence number. */
public final class DriverLocationTracker {
    private final DriverStateStore driverStateStore;
    private final DriverHeartbeatStore heartbeatStore;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();

    public DriverLocationTracker(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore");
    }

    /** Registers a driver before it starts publishing live updates. */
    public void registerDriver(long driverId, long timestampMillis) {
        requireDriver(driverId);
        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            if (!heartbeatStore.recordHeartbeat(driverId, 0L, timestampMillis)) {
                throw new IllegalStateException("Driver already has a registered heartbeat: " + driverId);
            }
        }
    }

    /**
     * Applies a live location update only when its sequence number is strictly
     * newer than the previously accepted sequence for the same driver.
     */
    public boolean update(DriverLocationUpdate update) {
        Objects.requireNonNull(update, "update");
        requireDriver(update.driverId());

        synchronized (driverLocks.computeIfAbsent(update.driverId(), ignored -> new Object())) {
            if (!heartbeatStore.recordHeartbeat(
                    update.driverId(),
                    update.sequenceNumber(),
                    update.timestampMillis())) {
                return false;
            }
            driverStateStore.updateLocation(update.driverId(), update.node());
            return true;
        }
    }

    private void requireDriver(long driverId) {
        driverStateStore.getDriver(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
    }
}
