package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.events.FleetEventPublisher;
import com.ganesh.fleetdispatch.events.FleetEventType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates the complete failure workflow for one driver. */
public final class DriverFailureRecoveryCoordinator {
    private final DriverStateStore driverStateStore;
    private final PickedUpOrderRecoveryService pickedUpRecoveryService;
    private final DispatchEngine dispatchEngine;
    private final DeliveryConstraints deliveryConstraints;
    private final FleetEventPublisher eventPublisher;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();

    public DriverFailureRecoveryCoordinator(
            DriverStateStore driverStateStore,
            PickedUpOrderRecoveryService pickedUpRecoveryService,
            DispatchEngine dispatchEngine,
            DeliveryConstraints deliveryConstraints) {
        this(
                driverStateStore,
                pickedUpRecoveryService,
                dispatchEngine,
                deliveryConstraints,
                null);
    }

    public DriverFailureRecoveryCoordinator(
            DriverStateStore driverStateStore,
            PickedUpOrderRecoveryService pickedUpRecoveryService,
            DispatchEngine dispatchEngine,
            DeliveryConstraints deliveryConstraints,
            FleetEventPublisher eventPublisher) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.pickedUpRecoveryService = Objects.requireNonNull(
                pickedUpRecoveryService,
                "pickedUpRecoveryService");
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        this.deliveryConstraints = Objects.requireNonNull(
                deliveryConstraints,
                "deliveryConstraints");
        this.eventPublisher = eventPublisher;
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

            NodeFailureSnapshot beforeFailure = new NodeFailureSnapshot(
                    driver.currentNode().value(),
                    driver.status().name());

            List<DriverRecoveryTask> pickedUpTasks = pickedUpRecoveryService
                    .queuePickedUpOrders(driverId, nowMillis);
            List<DispatchAssignment> reassigned = dispatchEngine
                    .reassignDriver(driverId, deliveryConstraints);

            publishFailureEvents(driverId, beforeFailure, pickedUpTasks, reassigned);

            return new DriverFailureDetection(
                    driverId,
                    pickedUpTasks.size(),
                    reassigned.size());
        }
    }

    private void publishFailureEvents(
            long driverId,
            NodeFailureSnapshot beforeFailure,
            List<DriverRecoveryTask> pickedUpTasks,
            List<DispatchAssignment> reassigned) {
        if (eventPublisher == null) {
            return;
        }

        String aggregateId = "driver-" + driverId;
        eventPublisher.publish(
                FleetEventType.DRIVER_OFFLINE,
                aggregateId,
                "DRIVER",
                Map.of(
                        "driverId", driverId,
                        "lastNode", beforeFailure.nodeId(),
                        "previousStatus", beforeFailure.status()));

        for (DriverRecoveryTask task : pickedUpTasks) {
            eventPublisher.publish(
                    FleetEventType.ORDER_RECOVERY_STARTED,
                    "order-" + task.orderId(),
                    "ORDER",
                    Map.of(
                            "orderId", task.orderId(),
                            "failedDriverId", task.failedDriverId(),
                            "handoffNode", task.handoffNode().value()));
        }

        for (DispatchAssignment assignment : reassigned) {
            eventPublisher.publish(
                    FleetEventType.ORDER_RECOVERY_ASSIGNED,
                    "order-" + assignment.orderId(),
                    "ORDER",
                    Map.of(
                            "orderId", assignment.orderId(),
                            "replacementDriverId", assignment.driverId(),
                            "assignmentType", "DISPATCH_REASSIGNMENT"));
        }
    }

    private record NodeFailureSnapshot(long nodeId, String status) {
    }
}
