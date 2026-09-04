package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Concurrent in-memory store that orders live driver updates by sequence number. */
public final class InMemoryDriverHeartbeatStore implements DriverHeartbeatStore {
    private final ConcurrentHashMap<Long, Heartbeat> heartbeats = new ConcurrentHashMap<>();

    @Override
    public boolean recordHeartbeat(long driverId, long sequenceNumber, long heartbeatTimestampMillis) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be non-negative");
        }
        if (heartbeatTimestampMillis < 0) {
            throw new IllegalArgumentException("heartbeatTimestampMillis must be non-negative");
        }

        AtomicBoolean accepted = new AtomicBoolean(false);
        heartbeats.compute(driverId, (id, current) -> {
            if (current != null && sequenceNumber <= current.sequenceNumber()) {
                return current;
            }
            accepted.set(true);
            return new Heartbeat(sequenceNumber, heartbeatTimestampMillis);
        });
        return accepted.get();
    }

    @Override
    public OptionalLong getLastHeartbeatMillis(long driverId) {
        Heartbeat heartbeat = heartbeats.get(driverId);
        return heartbeat == null ? OptionalLong.empty() : OptionalLong.of(heartbeat.timestampMillis());
    }

    @Override
    public OptionalLong getLastSequenceNumber(long driverId) {
        Heartbeat heartbeat = heartbeats.get(driverId);
        return heartbeat == null ? OptionalLong.empty() : OptionalLong.of(heartbeat.sequenceNumber());
    }

    @Override
    public List<Long> getTrackedDriverIds() {
        List<Long> ids = new ArrayList<>(heartbeats.keySet());
        ids.sort(Long::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public int size() {
        return heartbeats.size();
    }

    private record Heartbeat(long sequenceNumber, long timestampMillis) {
    }
}
