package com.ganesh.fleetdispatch.dispatch;

import java.util.List;

/** Queue of delivery recovery work. Backed by an in-memory queue for V2; Kafka can replace it later. */
public interface DriverRecoveryQueue {
    void enqueue(DriverRecoveryTask task);

    List<DriverRecoveryTask> drain(int maxItems);

    int size();
}
