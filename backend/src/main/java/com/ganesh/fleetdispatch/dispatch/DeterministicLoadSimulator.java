package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Reproducible synthetic workload generator for V3 load tests and demos. */
public final class DeterministicLoadSimulator {
    public record DriverSeed(long id, NodeId node) {}
    public record OrderSeed(long id, NodeId pickupNode, NodeId dropoffNode, long requestTimestamp) {}
    public record Scenario(List<DriverSeed> drivers, List<OrderSeed> orders) {}

    public Scenario generate(int driverCount, int orderCount, long seed, long startTimestampMillis, int nodeCount) {
        if (driverCount < 0 || orderCount < 0 || nodeCount <= 0 || startTimestampMillis < 0) {
            throw new IllegalArgumentException("invalid simulator configuration");
        }
        SplittableRandom random = new SplittableRandom(seed);
        List<DriverSeed> drivers = new ArrayList<>(driverCount);
        for (int i = 0; i < driverCount; i++) {
            drivers.add(new DriverSeed(i, randomNode(random, nodeCount)));
        }
        List<OrderSeed> orders = new ArrayList<>(orderCount);
        for (int i = 0; i < orderCount; i++) {
            NodeId pickup = randomNode(random, nodeCount);
            NodeId dropoff = randomNode(random, nodeCount);
            while (dropoff.equals(pickup) && nodeCount > 1) {
                dropoff = randomNode(random, nodeCount);
            }
            orders.add(new OrderSeed(i, pickup, dropoff, startTimestampMillis + i * 1_000L));
        }
        return new Scenario(List.copyOf(drivers), List.copyOf(orders));
    }

    private static NodeId randomNode(SplittableRandom random, int nodeCount) {
        return new NodeId(random.nextLong(nodeCount));
    }
}
