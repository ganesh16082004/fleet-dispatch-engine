package com.ganesh.fleetdispatch.cache;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Service
public final class RedisDriverLocationCache implements DriverLocationCache {
    private static final String KEY_PREFIX = "fleet:driver:location:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisDriverLocationCache(StringRedisTemplate redis) {
        this(redis, DEFAULT_TTL);
    }

    public RedisDriverLocationCache(StringRedisTemplate redis, Duration ttl) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    @Override
    public void put(long driverId, NodeId node) {
        validateDriverId(driverId);
        Objects.requireNonNull(node, "node");
        redis.opsForValue().set(key(driverId), Long.toString(node.value()), ttl);
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
