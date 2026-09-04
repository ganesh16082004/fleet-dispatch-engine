package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

            DriverRoutePlan currentPlan = driverRouteStore.getPlan(driverId).orElse(null);
            if (currentPlan == null) {
                return List.of();
            }

            List<DriverRecoveryTask> tasks = new ArrayList<>();
            List<Order> remainingOrders = new ArrayList<>();

            for (Order routeOrder : currentPlan.activeOrders()) {
                Optional<Order> current = orderStateStore.getOrder(routeOrder.id());
                if (current.isEmpty()) {
                    continue;
                }

                Order order = current.get();
                if (order.status() != OrderStatus.PICKED_UP) {
                    remainingOrders.add(order);
                    continue;
                }

                Optional<DriverRecoveryTask> recoveryTask = markForRecovery(
                        driverId,
                        order.id(),
                        handoffNode,
                        nowMillis);
                recoveryTask.ifPresentOrElse(
                        tasks::add,
                        () -> {
                            Order latest = orderStateStore.getOrder(order.id()).orElse(order);
                            if (latest.status() == OrderStatus.ASSIGNED
                                    || latest.status() == OrderStatus.PICKED_UP) {
                                remainingOrders.add(latest);
                            }
                        });
            }

            Set<Long> remainingOrderIds = remainingOrders.stream()
                    .map(Order::id)
                    .collect(Collectors.toSet());
            List<RouteStop> remainingStops = currentPlan.stops().stream()
                    .filter(stop -> remainingOrderIds.contains(stop.orderId()))
                    .toList();

            if (remainingOrders.isEmpty()) {
                driverRouteStore.remove(driverId);
            } else {
                driverRouteStore.putPlan(
                        driverId,
                        new DriverRoutePlan(remainingOrders, remainingStops));
            }

            return List.copyOf(tasks);
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
