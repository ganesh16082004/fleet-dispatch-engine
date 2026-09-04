package com.ganesh.fleetdispatch.dispatch;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Applies ordered live driver location updates and records their heartbeat. */
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

    /** Registers the initial heartbeat timestamp for a known driver. */
    public void registerDriver(long driverId, long timestampMillis) {
        requireDriver(driverId);
        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            heartbeatStore.recordHeartbeat(driverId, timestampMillis);
        }
    }

    /**
     * Applies a live location update only when its timestamp is newer than the
     * previously accepted heartbeat for the same driver.
     */
    public boolean update(DriverLocationUpdate update) {
        Objects.requireNonNull(update, "update");
        requireDriver(update.driverId());

        synchronized (driverLocks.computeIfAbsent(update.driverId(), ignored -> new Object())) {
            if (!heartbeatStore.recordHeartbeat(update.driverId(), update.timestampMillis())) {
                return false;
            }
            driverStateStore.updateLocation(update.driverId(), update.node());
            if (driverStateStore.getDriver(update.driverId())
                    .map(driver -> driver.status() == DriverStatus.OFFLINE)
                    .orElse(false)) {
                driverStateStore.updateStatus(update.driverId(), DriverStatus.AVAILABLE);
            }
            return true;
        }
    }

    private void requireDriver(long driverId) {
        driverStateStore.getDriver(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
    }
}
