package com.ganesh.fleetdispatch.cache;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDriverLocationCacheUnitTest {
    @Test
    void putStoresLocationWithConfiguredTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        Duration ttl = Duration.ofMinutes(10);
        RedisDriverLocationCache cache = new RedisDriverLocationCache(redis, ttl);

        cache.put(103L, new NodeId(101L));

        verify(values).set("fleet:driver:location:103", "101", ttl);
    }

    @Test
    void getReturnsStoredLocation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fleet:driver:location:103")).thenReturn("101");

        RedisDriverLocationCache cache = new RedisDriverLocationCache(redis, Duration.ofMinutes(10));

        assertEquals(Optional.of(new NodeId(101L)), cache.get(103L));
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        assertThrows(IllegalArgumentException.class,
                () -> new RedisDriverLocationCache(redis, Duration.ZERO));
    }
}
