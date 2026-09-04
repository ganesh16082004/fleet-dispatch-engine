package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Concurrent in-memory heartbeat store that ignores out-of-order updates. */
public final class InMemoryDriverHeartbeatStore implements DriverHeartbeatStore {
    private final ConcurrentHashMap<Long, Long> lastHeartbeats = new ConcurrentHashMap<>();

    @Override
    public boolean recordHeartbeat(long driverId, long heartbeatTimestampMillis) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (heartbeatTimestampMillis < 0) {
            throw new IllegalArgumentException("heartbeatTimestampMillis must be non-negative");
        }

        AtomicBoolean accepted = new AtomicBoolean(false);
        lastHeartbeats.compute(driverId, (id, current) -> {
            if (current != null && heartbeatTimestampMillis < current) {
                return current;
            }
            accepted.set(true);
            return heartbeatTimestampMillis;
        });
        return accepted.get();
    }

    @Override
    public OptionalLong getLastHeartbeatMillis(long driverId) {
        Long timestamp = lastHeartbeats.get(driverId);
        return timestamp == null ? OptionalLong.empty() : OptionalLong.of(timestamp);
    }

    @Override
    public List<Long> getTrackedDriverIds() {
        List<Long> ids = new ArrayList<>(lastHeartbeats.keySet());
        ids.sort(Long::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public int size() {
        return lastHeartbeats.size();
    }
}
