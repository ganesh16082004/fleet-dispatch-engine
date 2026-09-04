package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.OptionalLong;

/** Thread-safe storage for the latest accepted driver update metadata. */
public interface DriverHeartbeatStore {
    /** Starts a new driver session and resets its sequence window. */
    void startSession(long driverId, long sessionStartTimestampMillis);

    /**
     * Records a driver update only when its sequence number is strictly newer
     * than the currently stored sequence for that driver session.
     */
    boolean recordHeartbeat(long driverId, long sequenceNumber, long heartbeatTimestampMillis);

    OptionalLong getLastHeartbeatMillis(long driverId);

    OptionalLong getLastSequenceNumber(long driverId);

    List<Long> getTrackedDriverIds();

    int size();
}
