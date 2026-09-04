package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates the complete failure workflow for one driver. */
public final class DriverFailureRecoveryCoordinator {
    private final DriverStateStore driverStateStore;
    private final PickedUpOrderRecoveryService pickedUpRecoveryService;
    private final DispatchEngine dispatchEngine;
    private final DeliveryConstraints deliveryConstraints;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();

    public DriverFailureRecoveryCoordinator(
            DriverStateStore driverStateStore,
            PickedUpOrderRecoveryService pickedUpRecoveryService,
            DispatchEngine dispatchEngine,
            DeliveryConstraints deliveryConstraints) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.pickedUpRecoveryService = Objects.requireNonNull(
                pickedUpRecoveryService,
                "pickedUpRecoveryService");
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        this.deliveryConstraints = Objects.requireNonNull(
                deliveryConstraints,
                "deliveryConstraints");
    }

    /**
     * Executes the complete recovery workflow for a stale driver.
     *
     * <p>ASSIGNED orders return to normal dispatch while PICKED_UP orders become
     * explicit recovery work. Recovery for a given driver is serialized so two
     * detector threads cannot process the same failure simultaneously.</p>
     */
    public DriverFailureDetection recover(long driverId, long nowMillis) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }

        Object lock = driverLocks.computeIfAbsent(driverId, ignored -> new Object());
        synchronized (lock) {
            Driver driver = driverStateStore.getDriver(driverId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
            if (driver.status() == DriverStatus.OFFLINE) {
                return new DriverFailureDetection(driverId, 0, 0);
            }

            List<DriverRecoveryTask> pickedUpTasks = pickedUpRecoveryService
                    .queuePickedUpOrders(driverId, nowMillis);
            List<DispatchAssignment> reassigned = dispatchEngine
                    .reassignDriver(driverId, deliveryConstraints);

            return new DriverFailureDetection(
                    driverId,
                    pickedUpTasks.size(),
                    reassigned.size());
        }
    }
}
