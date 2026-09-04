package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.OptionalLong;

/** Thread-safe storage for the latest accepted heartbeat timestamp of each driver. */
public interface DriverHeartbeatStore {
    /**
     * Records a heartbeat when it is not older than the currently stored timestamp.
     * Returns false when the update is stale.
     */
    boolean recordHeartbeat(long driverId, long heartbeatTimestampMillis);

    OptionalLong getLastHeartbeatMillis(long driverId);

    List<Long> getTrackedDriverIds();

    int size();
}
