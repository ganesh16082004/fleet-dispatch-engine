package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory concurrent route-plan store. */
public final class InMemoryDriverRouteStore implements DriverRouteStore {
    private final ConcurrentHashMap<Long, DriverRoutePlan> plans = new ConcurrentHashMap<>();

    @Override
    public Optional<DriverRoutePlan> getPlan(long driverId) {
        return Optional.ofNullable(plans.get(driverId));
    }

    @Override
    public List<Long> getActiveDriverIds() {
        return plans.keySet().stream().sorted().toList();
    }

    @Override
    public void putPlan(long driverId, DriverRoutePlan plan) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (plan == null) {
            throw new NullPointerException("plan must not be null");
        }
        if (plan.activeOrders().isEmpty()) {
            plans.remove(driverId);
        } else {
            plans.put(driverId, plan);
        }
    }

    @Override
    public void remove(long driverId) {
        plans.remove(driverId);
    }

    @Override
    public void pruneInactive(OrderStateStore orderStateStore) {
        if (orderStateStore == null) {
            throw new NullPointerException("orderStateStore must not be null");
        }

        plans.forEach((driverId, plan) -> {
            List<Order> activeOrders = new ArrayList<>();
            plan.activeOrders().forEach(order ->
                    orderStateStore.getOrder(order.id()).ifPresent(current -> {
                        if (current.status() == OrderStatus.ASSIGNED
                                || current.status() == OrderStatus.PICKED_UP) {
                            activeOrders.add(current);
                        }
                    }));

            if (activeOrders.isEmpty()) {
                plans.remove(driverId, plan);
                return;
            }

            if (activeOrders.size() == plan.activeOrders().size()) {
                return;
            }

            var activeIds = activeOrders.stream().map(Order::id).collect(java.util.stream.Collectors.toSet());
            List<RouteStop> stops = plan.stops().stream()
                    .filter(stop -> activeIds.contains(stop.orderId()))
                    .toList();
            plans.replace(driverId, plan, new DriverRoutePlan(activeOrders, stops));
        });
    }
}
