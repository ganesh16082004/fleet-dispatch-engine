package com.ganesh.fleetdispatch.dispatch;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory idempotency store suitable for V3 tests and single-process execution. */
public final class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentHashMap<String, Boolean> keys = new ConcurrentHashMap<>();

    @Override
    public boolean tryStart(String key) {
        validate(key);
        return keys.putIfAbsent(key, Boolean.FALSE) == null;
    }

    @Override
    public void complete(String key) {
        validate(key);
        keys.put(key, Boolean.TRUE);
    }

    @Override
    public void remove(String key) {
        validate(key);
        keys.remove(key);
    }

    @Override
    public boolean contains(String key) {
        validate(key);
        return keys.containsKey(key);
    }

    @Override
    public int size() {
        return keys.size();
    }

    private static void validate(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
