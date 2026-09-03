package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Order;
import com.ganesh.fleetdispatch.domain.Rider;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable policy used by the dispatch engine to choose a rider for an order.
 * Implementations must not mutate the supplied state.
 */
public interface DispatchStrategy {
    Optional<Rider> selectRider(Order order, List<Rider> candidates);
}
