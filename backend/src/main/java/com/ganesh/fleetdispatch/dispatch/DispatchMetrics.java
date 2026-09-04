package com.ganesh.fleetdispatch.dispatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Lock-free counters for V3 operational observability. */
public final class DispatchMetrics {
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(requireName(name), ignored -> new AtomicLong()).incrementAndGet();
    }

    public void add(String name, long delta) {
        counters.computeIfAbsent(requireName(name), ignored -> new AtomicLong()).addAndGet(delta);
    }

    public long count(String name) {
        AtomicLong counter = counters.get(name);
        return counter == null ? 0L : counter.get();
    }

    public Map<String, Long> snapshot() {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get()));
    }

    public void reset() {
        counters.clear();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("metric name must not be blank");
        }
        return name;
    }
}
