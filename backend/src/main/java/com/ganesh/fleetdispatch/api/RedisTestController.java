package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.cache.DriverLocationCache;
import com.ganesh.fleetdispatch.graph.NodeId;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RedisTestController {
    private final DriverLocationCache driverLocationCache;

    public RedisTestController(DriverLocationCache driverLocationCache) {
        this.driverLocationCache = driverLocationCache;
    }

    @PostMapping("/api/v1/redis/test")
    public Map<String, Object> testRedis() {
        long driverId = 999_999L;
        NodeId node = new NodeId(12345L);

        driverLocationCache.put(driverId, node);
        NodeId stored = driverLocationCache.get(driverId)
                .orElseThrow(() -> new IllegalStateException("Redis write succeeded but value was not readable"));
        driverLocationCache.remove(driverId);

        return Map.of(
                "status", "UP",
                "backend", "redis-cloud",
                "driverId", driverId,
                "storedNode", stored.value(),
                "message", "Spring Boot successfully wrote to and read from Redis Cloud");
    }
}
