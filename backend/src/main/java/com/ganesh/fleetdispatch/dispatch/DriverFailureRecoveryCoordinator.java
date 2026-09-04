package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.Objects;

/** Coordinates the complete failure workflow for one driver. */
public final class DriverFailureRecoveryCoordinator {
    private final DriverStateStore driverStateStore;
    private final PickedUpOrderRecoveryService pickedUpRecoveryService;
    private final DispatchEngine dispatchEngine;
    private final DeliveryConstraints deliveryConstraints;

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
     * explicit recovery work. The underlying state transitions are atomic, so a
     * repeated invocation cannot assign the same order twice.</p>
     */
    public DriverFailureDetection recover(long driverId, long nowMillis) {
        driverStateStore.getDriver(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
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
