package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Thread-safe in-memory recovery queue. */
public final class InMemoryDriverRecoveryQueue implements DriverRecoveryQueue {
    private final ConcurrentLinkedQueue<DriverRecoveryTask> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void enqueue(DriverRecoveryTask task) {
        if (task == null) {
            throw new NullPointerException("task must not be null");
        }
        queue.add(task);
    }

    @Override
    public List<DriverRecoveryTask> drain(int maxItems) {
        if (maxItems <= 0) {
            throw new IllegalArgumentException("maxItems must be positive");
        }

        List<DriverRecoveryTask> drained = new ArrayList<>(Math.min(maxItems, queue.size()));
        for (int i = 0; i < maxItems; i++) {
            DriverRecoveryTask task = queue.poll();
            if (task == null) {
                break;
            }
            drained.add(task);
        }
        return List.copyOf(drained);
    }

    @Override
    public int size() {
        return queue.size();
    }
}
