package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.OptionalLong;

/** Thread-safe storage for the latest accepted driver update metadata. */
public interface DriverHeartbeatStore {
    /**
     * Records a driver update only when its sequence number is strictly newer
     * than the currently stored sequence for that driver.
     */
    boolean recordHeartbeat(long driverId, long sequenceNumber, long heartbeatTimestampMillis);

    /**
     * Legacy registration helper. Initializes a driver with sequence zero.
     */
    default boolean recordHeartbeat(long driverId, long heartbeatTimestampMillis) {
        return recordHeartbeat(driverId, 0L, heartbeatTimestampMillis);
    }

    OptionalLong getLastHeartbeatMillis(long driverId);

    OptionalLong getLastSequenceNumber(long driverId);

    List<Long> getTrackedDriverIds();

    int size();
}
