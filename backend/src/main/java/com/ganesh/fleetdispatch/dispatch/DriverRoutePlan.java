package com.ganesh.fleetdispatch.dispatch;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable active-order plan for a driver. At most three orders may be active. */
public record DriverRoutePlan(List<Order> activeOrders, List<RouteStop> stops) {
    public static final int MAX_ACTIVE_DELIVERIES = 3;

    public DriverRoutePlan {
        Objects.requireNonNull(activeOrders, "activeOrders must not be null");
        Objects.requireNonNull(stops, "stops must not be null");

        activeOrders = activeOrders.stream()
                .map(DriverRoutePlan::normalizePendingOrder)
                .toList();
        stops = List.copyOf(stops);

        if (activeOrders.size() > MAX_ACTIVE_DELIVERIES) {
            throw new IllegalArgumentException(
                    "A driver may have at most " + MAX_ACTIVE_DELIVERIES + " active deliveries");
        }

        Set<Long> activeOrderIds = new HashSet<>();
        for (Order order : activeOrders) {
            Objects.requireNonNull(order, "activeOrders must not contain null");
            if (order.status() != OrderStatus.ASSIGNED && order.status() != OrderStatus.PICKED_UP) {
                throw new IllegalArgumentException(
                        "Only ASSIGNED and PICKED_UP orders may be active in a route plan");
            }
            if (!activeOrderIds.add(order.id())) {
                throw new IllegalArgumentException("Duplicate active order: " + order.id());
            }
        }

        Set<Long> seenStops = new HashSet<>();
        for (RouteStop stop : stops) {
            if (!activeOrderIds.contains(stop.orderId())) {
                throw new IllegalArgumentException(
                        "Route stop belongs to an order outside the active order set: " + stop.orderId());
            }
            if (stop.type() == RouteStopType.PICKUP
                    && activeOrders.stream()
                    .filter(order -> order.id() == stop.orderId())
                    .findFirst()
                    .map(order -> order.status() == OrderStatus.PICKED_UP)
                    .orElse(false)) {
                throw new IllegalArgumentException(
                        "PICKED_UP order cannot contain a PICKUP stop: " + stop.orderId());
            }
            if (!seenStops.add(stop.orderId() * 10L + stop.type().ordinal())) {
                throw new IllegalArgumentException("Duplicate route stop for order: " + stop.orderId());
            }
        }

        int expectedStops = activeOrders.stream()
                .mapToInt(order -> order.status() == OrderStatus.PICKED_UP ? 1 : 2)
                .sum();
        if (stops.size() != expectedStops) {
            throw new IllegalArgumentException(
                    "Route plan contains " + stops.size()
                            + " stops but expected " + expectedStops
                            + " for the active order states");
        }
    }

    public static DriverRoutePlan empty() {
        return new DriverRoutePlan(List.of(), List.of());
    }

    public static DriverRoutePlan single(Order order) {
        Objects.requireNonNull(order, "order");
        Order routeOrder = normalizePendingOrder(order);
        return routeOrder.status() == OrderStatus.PICKED_UP
                ? new DriverRoutePlan(
                List.of(routeOrder),
                List.of(new RouteStop(routeOrder.id(), RouteStopType.DROPOFF, routeOrder.dropoffNode())))
                : new DriverRoutePlan(
                List.of(routeOrder),
                List.of(
                        new RouteStop(routeOrder.id(), RouteStopType.PICKUP, routeOrder.pickupNode()),
                        new RouteStop(routeOrder.id(), RouteStopType.DROPOFF, routeOrder.dropoffNode())));
    }

    public int activeDeliveryCount() {
        return activeOrders.size();
    }

    private static Order normalizePendingOrder(Order order) {
        Objects.requireNonNull(order, "activeOrders must not contain null");
        if (order.status() != OrderStatus.CREATED) {
            return order;
        }
        return new Order(
                order.id(),
                order.pickupNode(),
                order.dropoffNode(),
                order.requestTimestamp(),
                OrderStatus.ASSIGNED);
    }
}
