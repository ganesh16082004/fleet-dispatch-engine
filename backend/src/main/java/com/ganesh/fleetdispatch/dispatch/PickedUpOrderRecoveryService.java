package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Moves picked-up deliveries into explicit recovery work when their driver fails. */
public final class PickedUpOrderRecoveryService {
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final DriverRouteStore driverRouteStore;
    private final DriverRecoveryQueue recoveryQueue;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();

    public PickedUpOrderRecoveryService(
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRouteStore driverRouteStore,
            DriverRecoveryQueue recoveryQueue) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.orderStateStore = Objects.requireNonNull(orderStateStore, "orderStateStore");
        this.driverRouteStore = Objects.requireNonNull(driverRouteStore, "driverRouteStore");
        this.recoveryQueue = Objects.requireNonNull(recoveryQueue, "recoveryQueue");
    }

    /** Marks the driver offline and queues each picked-up order for recovery. */
    public List<DriverRecoveryTask> queuePickedUpOrders(long driverId, long nowMillis) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }

        Driver driver = driverStateStore.getDriver(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
        NodeId handoffNode = driver.currentNode();

        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            driverStateStore.updateStatus(driverId, DriverStatus.OFFLINE);

            List<DriverRecoveryTask> tasks = driverRouteStore.getPlan(driverId)
                    .map(DriverRoutePlan::activeOrders)
                    .orElse(List.of())
                    .stream()
                    .map(order -> orderStateStore.getOrder(order.id())
                            .filter(current -> current.status() == OrderStatus.PICKED_UP)
                            .flatMap(current -> markForRecovery(driverId, current.id(), handoffNode, nowMillis)))
                    .flatMap(Optional::stream)
                    .toList();

            driverRouteStore.remove(driverId);
            return tasks;
        }
    }

    private Optional<DriverRecoveryTask> markForRecovery(
            long failedDriverId,
            long orderId,
            NodeId handoffNode,
            long nowMillis) {
        if (!orderStateStore.tryTransition(
                orderId,
                OrderStatus.PICKED_UP,
                OrderStatus.RECOVERY_REQUIRED)) {
            return Optional.empty();
        }

        DriverRecoveryTask task = new DriverRecoveryTask(
                failedDriverId,
                orderId,
                handoffNode,
                nowMillis);
        recoveryQueue.enqueue(task);
        return Optional.of(task);
    }
}
