package com.ganesh.fleetdispatch.dispatch;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.List;

/** Schedules recovery retries without blocking worker threads. */
public final class RecoveryRetryScheduler {
    private record ScheduledTask(DriverRecoveryTask task, int attempt, long dueAtMillis) {}

    private final RecoveryRetryPolicy policy;
    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>(
            Comparator.comparingLong(ScheduledTask::dueAtMillis)
                    .thenComparingLong(t -> t.task().orderId()));
    private final Map<Long, Integer> attemptsByOrder = new HashMap<>();

    public RecoveryRetryScheduler(RecoveryRetryPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized boolean schedule(DriverRecoveryTask task, long nowMillis) {
        Objects.requireNonNull(task, "task");
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis must be non-negative");
        }
        int attempt = attemptsByOrder.merge(task.orderId(), 1, Integer::sum);
        if (policy.exhausted(attempt)) {
            return false;
        }
        queue.add(new ScheduledTask(task, attempt, nowMillis + policy.delayMillis(attempt)));
        return true;
    }

    public synchronized List<DriverRecoveryTask> drainDue(long nowMillis, int maxItems) {
        if (nowMillis < 0 || maxItems <= 0) {
            throw new IllegalArgumentException("invalid drain arguments");
        }
        java.util.ArrayList<DriverRecoveryTask> due = new java.util.ArrayList<>();
        while (due.size() < maxItems && !queue.isEmpty() && queue.peek().dueAtMillis() <= nowMillis) {
            ScheduledTask scheduled = queue.poll();
            due.add(scheduled.task());
        }
        return List.copyOf(due);
    }

    public synchronized void markSucceeded(long orderId) {
        attemptsByOrder.remove(orderId);
    }

    public synchronized int pending() {
        return queue.size();
    }

    public synchronized int attempts(long orderId) {
        return attemptsByOrder.getOrDefault(orderId, 0);
    }
}
