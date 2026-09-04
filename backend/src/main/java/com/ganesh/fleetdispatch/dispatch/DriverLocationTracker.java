package com.ganesh.fleetdispatch.dispatch;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies live driver location updates ordered by session and per-driver sequence number. */
public final class DriverLocationTracker {
    private final DriverStateStore driverStateStore;
    private final DriverHeartbeatStore heartbeatStore;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, UUID> activeSessions = new ConcurrentHashMap<>();

    public DriverLocationTracker(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore");
    }

    /** Registers a driver using a newly generated live-tracking session. */
    public UUID registerDriver(long driverId, long timestampMillis) {
        UUID sessionId = UUID.randomUUID();
        registerSession(driverId, sessionId, timestampMillis);
        return sessionId;
    }

    /** Registers an explicit connection session for a known, non-offline driver. */
    public void registerSession(long driverId, UUID sessionId, long timestampMillis) {
        requireDriver(driverId);
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be non-negative");
        }

        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            if (driverStateStore.getDriver(driverId)
                    .map(driver -> driver.status() == DriverStatus.OFFLINE)
                    .orElse(false)) {
                throw new IllegalStateException("Offline driver cannot start a tracking session: " + driverId);
            }
            activeSessions.put(driverId, sessionId);
            if (!heartbeatStore.recordHeartbeat(driverId, 0L, timestampMillis)) {
                throw new IllegalStateException("Driver heartbeat timestamp is older than stored state: " + driverId);
            }
        }
    }

    /**
     * Applies a live location update only when it belongs to the active session
     * and its sequence number is strictly newer than the previously accepted one.
     */
    public boolean update(DriverLocationUpdate update) {
        Objects.requireNonNull(update, "update");
        requireDriver(update.driverId());

        synchronized (driverLocks.computeIfAbsent(update.driverId(), ignored -> new Object())) {
            UUID activeSession = activeSessions.get(update.driverId());
            if (activeSession == null) {
                activeSessions.put(update.driverId(), update.sessionId());
            } else if (!activeSession.equals(update.sessionId())) {
                return false;
            }

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

    /** Ends a connection session without changing the driver's availability state. */
    public boolean closeSession(long driverId, UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            return activeSessions.remove(driverId, sessionId);
        }
    }

    private void requireDriver(long driverId) {
        driverStateStore.getDriver(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
    }
}
