package com.ganesh.fleetdispatch.dispatch;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Concurrent in-memory order state backed by atomic map updates. */
public final class InMemoryOrderStateStore implements OrderStateStore {
    private final ConcurrentHashMap<Long, OrderState> orders = new ConcurrentHashMap<>();

    @Override
    public void addOrder(Order order) {
        Objects.requireNonNull(order, "order must not be null");

        OrderState previous = orders.putIfAbsent(
                order.id(),
                new OrderState(order, null)
        );
        if (previous != null) {
            throw new IllegalArgumentException("Order already exists: " + order.id());
        }
    }

    @Override
    public Optional<Order> getOrder(long orderId) {
        OrderState state = orders.get(orderId);
        return state == null ? Optional.empty() : Optional.of(state.order());
    }

    @Override
    public boolean tryAssign(long orderId, long driverId) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }

        AtomicBoolean assigned = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != OrderStatus.CREATED) {
                return current;
            }

            assigned.set(true);
            Order order = current.order();
            Order updated = new Order(
                    order.id(),
                    order.pickupNode(),
                    order.dropoffNode(),
                    order.requestTimestamp(),
                    OrderStatus.ASSIGNED
            );
            return new OrderState(updated, driverId);
        });

        return assigned.get();
    }

    @Override
    public boolean tryTransition(
            long orderId,
            OrderStatus expectedStatus,
            OrderStatus newStatus) {
        Objects.requireNonNull(expectedStatus, "expectedStatus must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        AtomicBoolean transitioned = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != expectedStatus) {
                return current;
            }

            transitioned.set(true);
            Order order = current.order();
            Order updated = new Order(
                    order.id(),
                    order.pickupNode(),
                    order.dropoffNode(),
                    order.requestTimestamp(),
                    newStatus
            );
            return new OrderState(updated, current.assignedDriverId());
        });

        return transitioned.get();
    }

    @Override
    public OptionalLong getAssignedDriverId(long orderId) {
        OrderState state = orders.get(orderId);
        if (state == null || state.assignedDriverId() == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(state.assignedDriverId());
    }

    @Override
    public int size() {
        return orders.size();
    }

    private record OrderState(Order order, Long assignedDriverId) {
    }
}
