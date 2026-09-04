package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.Optional;

/** Thread-safe store for active route plans used by route-consolidation dispatch. */
public interface DriverRouteStore {
    Optional<DriverRoutePlan> getPlan(long driverId);

    List<Long> getActiveDriverIds();

    void putPlan(long driverId, DriverRoutePlan plan);

    void remove(long driverId);

    /** Removes orders that are no longer ASSIGNED or PICKED_UP from the plan. */
    void pruneInactive(OrderStateStore orderStateStore);
}
