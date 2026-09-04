package com.ganesh.fleetdispatch.dispatch;

/** Thread-safe store for one-time processing keys. */
public interface IdempotencyStore {
    boolean tryStart(String key);
    void complete(String key);
    void remove(String key);
    boolean contains(String key);
    int size();
}
