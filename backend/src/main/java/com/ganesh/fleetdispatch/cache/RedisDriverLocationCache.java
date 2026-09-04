package com.ganesh.fleetdispatch.cache;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public final class RedisDriverLocationCache implements DriverLocationCache {
    private static final String KEY_PREFIX = "fleet:driver:location:";

    private final StringRedisTemplate redis;

    public RedisDriverLocationCache(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public void put(long driverId, NodeId node) {
        validateDriverId(driverId);
        Objects.requireNonNull(node, "node");
        redis.opsForValue().set(key(driverId), Long.toString(node.value()));
    }

    @Override
    public Optional<NodeId> get(long driverId) {
        validateDriverId(driverId);
        String value = redis.opsForValue().get(key(driverId));
        return value == null ? Optional.empty() : Optional.of(new NodeId(Long.parseLong(value)));
    }

    @Override
    public void remove(long driverId) {
        validateDriverId(driverId);
        redis.delete(key(driverId));
    }

    private static String key(long driverId) {
        return KEY_PREFIX + driverId;
    }

    private static void validateDriverId(long driverId) {
        if (driverId < 0) {
            throw new IllegalArgumentException("driverId must be non-negative");
        }
    }
}
