package com.ganesh.fleetdispatch.dispatch;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Concurrent in-memory order state backed by atomic map updates. */
public final class InMemoryOrderStateStore implements OrderStateStore {
    private final ConcurrentHashMap<Long, OrderState> orders = new ConcurrentHashMap<>();

    @Override
    public void addOrder(Order order) {
        Objects.requireNonNull(order, "order must not be null");

        OrderState previous = orders.putIfAbsent(order.id(), new OrderState(order, null));
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
        validateDriverId(driverId);
        AtomicBoolean assigned = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != OrderStatus.CREATED) {
                return current;
            }
            assigned.set(true);
            return new OrderState(withStatus(current.order(), OrderStatus.ASSIGNED), driverId);
        });
        return assigned.get();
    }

    @Override
    public boolean tryOffer(long orderId, long driverId) {
        validateDriverId(driverId);
        AtomicBoolean offered = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != OrderStatus.CREATED) {
                return current;
            }
            offered.set(true);
            return new OrderState(withStatus(current.order(), OrderStatus.OFFERED), driverId);
        });
        return offered.get();
    }

    @Override
    public boolean tryAcceptOffer(long orderId, long expectedDriverId) {
        validateDriverId(expectedDriverId);
        AtomicBoolean accepted = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != OrderStatus.OFFERED
                    || !matchesDriver(current, expectedDriverId)) {
                return current;
            }
            accepted.set(true);
            return new OrderState(withStatus(current.order(), OrderStatus.ASSIGNED), expectedDriverId);
        });
        return accepted.get();
    }

    @Override
    public boolean tryRejectOffer(long orderId, long expectedDriverId) {
        return releaseOffer(orderId, expectedDriverId);
    }

    @Override
    public boolean tryExpireOffer(long orderId, long expectedDriverId) {
        return releaseOffer(orderId, expectedDriverId);
    }

    private boolean releaseOffer(long orderId, long expectedDriverId) {
        validateDriverId(expectedDriverId);
        AtomicBoolean released = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != OrderStatus.OFFERED
                    || !matchesDriver(current, expectedDriverId)) {
                return current;
            }
            released.set(true);
            return new OrderState(withStatus(current.order(), OrderStatus.CREATED), null);
        });
        return released.get();
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
            return new OrderState(withStatus(current.order(), newStatus), current.assignedDriverId());
        });
        return transitioned.get();
    }

    @Override
    public boolean tryCancel(long orderId) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            OrderStatus status = current.order().status();
            if (status != OrderStatus.CREATED
                    && status != OrderStatus.ASSIGNED
                    && status != OrderStatus.PICKED_UP) {
                return current;
            }
            cancelled.set(true);
            return new OrderState(withStatus(current.order(), OrderStatus.CANCELLED), null);
        });
        return cancelled.get();
    }

    @Override
    public boolean tryRequeue(long orderId, long expectedDriverId) {
        validateDriverId(expectedDriverId);
        AtomicBoolean requeued = new AtomicBoolean(false);
        orders.computeIfPresent(orderId, (id, current) -> {
            if (current.order().status() != OrderStatus.ASSIGNED
                    || !matchesDriver(current, expectedDriverId)) {
                return current;
            }
            requeued.set(true);
            return new OrderState(withStatus(current.order(), OrderStatus.CREATED), null);
        });
        return requeued.get();
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

    private static boolean matchesDriver(OrderState state, long driverId) {
        return state.assignedDriverId() != null && state.assignedDriverId() == driverId;
    }

    private static Order withStatus(Order order, OrderStatus status) {
        return new Order(
                order.id(),
                order.pickupNode(),
                order.dropoffNode(),
                order.requestTimestamp(),
                status);
    }

    private static void validateDriverId(long driverId) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
    }

    private record OrderState(Order order, Long assignedDriverId) {
    }
}
