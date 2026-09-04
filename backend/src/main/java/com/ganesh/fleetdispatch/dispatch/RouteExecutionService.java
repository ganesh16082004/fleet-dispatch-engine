package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Executes assigned delivery routes and advances order lifecycle atomically per driver. */
public final class RouteExecutionService {
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final DriverRouteStore driverRouteStore;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();

    public RouteExecutionService(
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRouteStore driverRouteStore) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.orderStateStore = Objects.requireNonNull(orderStateStore, "orderStateStore");
        this.driverRouteStore = Objects.requireNonNull(driverRouteStore, "driverRouteStore");
    }

    /** Marks an assigned order picked up and removes its pickup stop from the active route. */
    public boolean markPickedUp(long orderId, long driverId) {
        validateIds(orderId, driverId);
        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            Order order = requireOwnedOrder(orderId, driverId, OrderStatus.ASSIGNED);
            if (!orderStateStore.tryTransition(orderId, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)) {
                return false;
            }

            Order pickedUp = orderStateStore.getOrder(orderId).orElse(order);
            updateRouteAfterPickup(driverId, pickedUp);
            return true;
        }
    }

    /** Marks a picked-up order completed and removes it from the driver's active route. */
    public boolean completeOrder(long orderId, long driverId) {
        validateIds(orderId, driverId);
        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            requireOwnedOrder(orderId, driverId, OrderStatus.PICKED_UP);
            if (!orderStateStore.tryTransition(orderId, OrderStatus.PICKED_UP, OrderStatus.COMPLETED)) {
                return false;
            }

            removeOrderFromRoute(driverId, orderId);
            if (driverRouteStore.getPlan(driverId).isEmpty()) {
                driverStateStore.updateStatus(driverId, DriverStatus.AVAILABLE);
            }
            return true;
        }
    }

    /** Returns the driver's current next stop, if any. */
    public Optional<RouteStop> nextStop(long driverId) {
        validateDriverId(driverId);
        return driverRouteStore.getPlan(driverId)
                .flatMap(plan -> plan.stops().stream().findFirst());
    }

    /**
     * Completes the driver's current route stop after the caller has verified arrival.
     * PICKUP stops advance an order to PICKED_UP; HANDOFF stops are treated as the
     * transition into the existing picked-up order; DROPOFF stops complete the order.
     */
    public boolean completeCurrentStop(long driverId, NodeId arrivedNode) {
        validateDriverId(driverId);
        Objects.requireNonNull(arrivedNode, "arrivedNode");

        synchronized (driverLocks.computeIfAbsent(driverId, ignored -> new Object())) {
            DriverRoutePlan plan = driverRouteStore.getPlan(driverId).orElse(null);
            if (plan == null || plan.stops().isEmpty()) {
                return false;
            }

            RouteStop currentStop = plan.stops().get(0);
            if (!currentStop.nodeId().equals(arrivedNode)) {
                return false;
            }

            return switch (currentStop.type()) {
                case PICKUP -> markPickedUp(orderIdFrom(currentStop), driverId);
                case HANDOFF -> {
                    Order order = requireOwnedOrder(orderIdFrom(currentStop), driverId, OrderStatus.ASSIGNED);
                    yield orderStateStore.tryTransition(
                            order.id(),
                            OrderStatus.ASSIGNED,
                            OrderStatus.PICKED_UP)
                            && removeStopOnly(driverId, currentStop);
                }
                case DROPOFF -> completeOrder(orderIdFrom(currentStop), driverId);
            };
        }
    }

    private Order requireOwnedOrder(long orderId, long driverId, OrderStatus expectedStatus) {
        Optional<Order> order = orderStateStore.getOrder(orderId);
        if (order.isEmpty()) {
            throw new IllegalArgumentException("Unknown order: " + orderId);
        }

        OptionalLongValue assignedDriver = new OptionalLongValue(orderStateStore.getAssignedDriverId(orderId));
        if (!assignedDriver.isPresent() || assignedDriver.getAsLong() != driverId) {
            throw new IllegalStateException(
                    "Order " + orderId + " is not assigned to driver " + driverId);
        }
        if (order.get().status() != expectedStatus) {
            throw new IllegalStateException(
                    "Order " + orderId + " is " + order.get().status()
                            + ", expected " + expectedStatus);
        }
        return order.get();
    }

    private void updateRouteAfterPickup(long driverId, Order pickedUpOrder) {
        DriverRoutePlan plan = driverRouteStore.getPlan(driverId).orElse(null);
        if (plan == null) {
            throw new IllegalStateException("No active route plan for driver " + driverId);
        }

        List<RouteStop> remainingStops = plan.stops().stream()
                .filter(stop -> !(stop.orderId() == pickedUpOrder.id()
                        && stop.type() == RouteStopType.PICKUP))
                .toList();
        List<Order> refreshedOrders = plan.activeOrders().stream()
                .map(order -> order.id() == pickedUpOrder.id() ? pickedUpOrder : order)
                .toList();
        driverRouteStore.putPlan(driverId, new DriverRoutePlan(refreshedOrders, remainingStops));
    }

    private boolean removeStopOnly(long driverId, RouteStop stop) {
        DriverRoutePlan plan = driverRouteStore.getPlan(driverId).orElse(null);
        if (plan == null) {
            return false;
        }

        List<RouteStop> remainingStops = new ArrayList<>(plan.stops());
        if (!remainingStops.remove(stop)) {
            return false;
        }
        Order updated = orderStateStore.getOrder(stop.orderId()).orElseThrow();
        List<Order> refreshedOrders = plan.activeOrders().stream()
                .map(order -> order.id() == updated.id() ? updated : order)
                .toList();
        driverRouteStore.putPlan(driverId, new DriverRoutePlan(refreshedOrders, remainingStops));
        return true;
    }

    private void removeOrderFromRoute(long driverId, long orderId) {
        driverRouteStore.getPlan(driverId).ifPresent(plan -> {
            List<Order> remainingOrders = plan.activeOrders().stream()
                    .filter(order -> order.id() != orderId)
                    .toList();
            List<RouteStop> remainingStops = plan.stops().stream()
                    .filter(stop -> stop.orderId() != orderId)
                    .toList();
            if (remainingOrders.isEmpty()) {
                driverRouteStore.remove(driverId);
            } else {
                driverRouteStore.putPlan(
                        driverId,
                        new DriverRoutePlan(remainingOrders, remainingStops));
            }
        });
    }

    private static long orderIdFrom(RouteStop stop) {
        return stop.orderId();
    }

    private static void validateIds(long orderId, long driverId) {
        if (orderId < 0) {
            throw new IllegalArgumentException("orderId must be non-negative");
        }
        validateDriverId(driverId);
    }

    private static void validateDriverId(long driverId) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
    }

    private record OptionalLongValue(java.util.OptionalLong value) {
        boolean isPresent() {
            return value.isPresent();
        }

        long getAsLong() {
            return value.getAsLong();
        }
    }
}
