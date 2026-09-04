package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.Objects;

/** Immutable active-order plan for a driver. At most three orders may be active. */
public record DriverRoutePlan(List<Order> activeOrders, List<RouteStop> stops) {
    public static final int MAX_ACTIVE_DELIVERIES = 3;

    public DriverRoutePlan {
        Objects.requireNonNull(activeOrders, "activeOrders must not be null");
        Objects.requireNonNull(stops, "stops must not be null");

        activeOrders = List.copyOf(activeOrders);
        stops = List.copyOf(stops);

        if (activeOrders.size() > MAX_ACTIVE_DELIVERIES) {
            throw new IllegalArgumentException(
                    "A driver may have at most " + MAX_ACTIVE_DELIVERIES + " active deliveries");
        }
        if (stops.size() != activeOrders.size() * 2) {
            throw new IllegalArgumentException("Each active order must have one pickup and one drop-off stop");
        }
    }

    public static DriverRoutePlan empty() {
        return new DriverRoutePlan(List.of(), List.of());
    }

    public static DriverRoutePlan single(Order order) {
        Objects.requireNonNull(order, "order");
        return new DriverRoutePlan(
                List.of(order),
                List.of(
                        new RouteStop(order.id(), RouteStopType.PICKUP, order.pickupNode()),
                        new RouteStop(order.id(), RouteStopType.DROPOFF, order.dropoffNode())));
    }

    public int activeDeliveryCount() {
        return activeOrders.size();
    }
}
