package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Detects drivers that have stopped sending heartbeats and starts the correct recovery path. */
public final class DriverFailureDetector {
    private static final DeliveryConstraints DEFAULT_RECOVERY_CONSTRAINTS =
            new DeliveryConstraints(300.0, 1_800.0);

    private final DriverStateStore driverStateStore;
    private final DriverHeartbeatStore heartbeatStore;
    private final DriverFailureRecoveryCoordinator recoveryCoordinator;
    private final long heartbeatTimeoutMillis;

    public DriverFailureDetector(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore,
            PickedUpOrderRecoveryService pickedUpRecoveryService,
            DispatchEngine dispatchEngine,
            long heartbeatTimeoutMillis) {
        this(
                driverStateStore,
                heartbeatStore,
                new DriverFailureRecoveryCoordinator(
                        driverStateStore,
                        pickedUpRecoveryService,
                        dispatchEngine,
                        DEFAULT_RECOVERY_CONSTRAINTS),
                heartbeatTimeoutMillis);
    }

    public DriverFailureDetector(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore,
            DriverFailureRecoveryCoordinator recoveryCoordinator,
            long heartbeatTimeoutMillis) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore");
        this.recoveryCoordinator = Objects.requireNonNull(recoveryCoordinator, "recoveryCoordinator");
        if (heartbeatTimeoutMillis <= 0) {
            throw new IllegalArgumentException("heartbeatTimeoutMillis must be positive");
        }
        this.heartbeatTimeoutMillis = heartbeatTimeoutMillis;
    }

    /**
     * Scans all tracked drivers and triggers the complete recovery workflow for
     * drivers whose latest accepted heartbeat is older than the configured timeout.
     */
    public List<DriverFailureDetection> detect(long nowMillis) {
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }

        List<DriverFailureDetection> detections = new ArrayList<>();
        for (long driverId : heartbeatStore.getTrackedDriverIds()) {
            Driver driver = driverStateStore.getDriver(driverId).orElse(null);
            if (driver == null || driver.status() == DriverStatus.OFFLINE) {
                continue;
            }

            OptionalLong lastHeartbeat = heartbeatStore.getLastHeartbeatMillis(driverId);
            if (lastHeartbeat.isEmpty()) {
                continue;
            }

            long elapsed = nowMillis - lastHeartbeat.getAsLong();
            if (elapsed < heartbeatTimeoutMillis) {
                continue;
            }

            detections.add(recoveryCoordinator.recover(driverId, nowMillis));
        }
        return List.copyOf(detections);
    }
}
