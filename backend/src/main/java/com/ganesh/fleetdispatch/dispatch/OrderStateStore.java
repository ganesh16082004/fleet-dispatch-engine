package com.ganesh.fleetdispatch.dispatch;

import java.util.Optional;
import java.util.OptionalLong;

/** Thread-safe store for the mutable lifecycle state of orders. */
public interface OrderStateStore {
    void addOrder(Order order);

    Optional<Order> getOrder(long orderId);

    /**
     * Atomically claims a CREATED order for a driver and transitions it to ASSIGNED.
     * Returns false if the order does not exist or is no longer CREATED.
     */
    boolean tryAssign(long orderId, long driverId);

    /** Atomically transitions an order from one expected state to another. */
    boolean tryTransition(long orderId, OrderStatus expectedStatus, OrderStatus newStatus);

    /** Returns the driver assigned by a successful tryAssign, if any. */
    OptionalLong getAssignedDriverId(long orderId);

    int size();
}
