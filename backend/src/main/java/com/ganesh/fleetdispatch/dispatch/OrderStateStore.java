package com.ganesh.fleetdispatch.dispatch;

import java.util.Optional;
import java.util.OptionalLong;

/** Thread-safe store for the mutable lifecycle state of orders. */
public interface OrderStateStore {
    void addOrder(Order order);

    Optional<Order> getOrder(long orderId);

    /** Atomically claims a CREATED order for a driver and transitions it to ASSIGNED. */
    boolean tryAssign(long orderId, long driverId);

    /** Atomically offers a CREATED order to a driver. */
    boolean tryOffer(long orderId, long driverId);

    /** Atomically accepts an OFFERED order and transitions it to ASSIGNED. */
    boolean tryAcceptOffer(long orderId, long expectedDriverId);

    /** Atomically rejects an OFFERED order and returns it to CREATED. */
    boolean tryRejectOffer(long orderId, long expectedDriverId);

    /** Atomically expires an OFFERED order and returns it to CREATED. */
    boolean tryExpireOffer(long orderId, long expectedDriverId);

    /** Atomically transitions an order from one expected state to another. */
    boolean tryTransition(long orderId, OrderStatus expectedStatus, OrderStatus newStatus);

    /** Atomically cancels an order and clears any attached driver. */
    boolean tryCancel(long orderId);

    /** Atomically moves an assigned order back to CREATED and clears its driver. */
    boolean tryRequeue(long orderId, long expectedDriverId);

    /** Returns the driver currently attached to an assigned or offered order, if any. */
    OptionalLong getAssignedDriverId(long orderId);

    int size();
}
