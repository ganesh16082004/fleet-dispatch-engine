package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Detects drivers that have stopped sending heartbeats and starts the correct recovery path. */
public final class DriverFailureDetector {
    private final DriverStateStore driverStateStore;
    private final DriverHeartbeatStore heartbeatStore;
    private final PickedUpOrderRecoveryService pickedUpRecoveryService;
    private final DispatchEngine dispatchEngine;
    private final long heartbeatTimeoutMillis;

    public DriverFailureDetector(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore,
            PickedUpOrderRecoveryService pickedUpRecoveryService,
            DispatchEngine dispatchEngine,
            long heartbeatTimeoutMillis) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore");
        this.pickedUpRecoveryService = Objects.requireNonNull(pickedUpRecoveryService, "pickedUpRecoveryService");
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        if (heartbeatTimeoutMillis <= 0) {
            throw new IllegalArgumentException("heartbeatTimeoutMillis must be positive");
        }
        this.heartbeatTimeoutMillis = heartbeatTimeoutMillis;
    }

    /**
     * Scans all tracked drivers and triggers recovery for drivers whose latest
     * accepted heartbeat is older than the configured timeout.
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

            List<DriverRecoveryTask> recoveryTasks = pickedUpRecoveryService
                    .queuePickedUpOrders(driverId, nowMillis);
            List<DispatchAssignment> reassigned = dispatchEngine.reassignDriver(driverId);
            detections.add(new DriverFailureDetection(
                    driverId,
                    recoveryTasks.size(),
                    reassigned.size()));
        }
        return List.copyOf(detections);
    }
}
