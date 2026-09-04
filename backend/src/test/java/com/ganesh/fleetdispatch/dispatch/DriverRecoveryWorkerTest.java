package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DriverRecoveryWorkerTest {
    private final NodeId failedDriverNode = new NodeId(1L);
    private final NodeId replacementNear = new NodeId(2L);
    private final NodeId replacementFar = new NodeId(3L);
    private final NodeId handoff = new NodeId(4L);
    private final NodeId blockedHandoff = new NodeId(7L);
    private final NodeId dropoff = new NodeId(5L);

    private RecoveryCandidateSelector.RouteFinder routeFinder() {
        return (source, target) -> {
            if (source.equals(replacementNear) && target.equals(handoff)) {
                return Optional.of(route(source, target, 10.0, 100.0));
            }
            if (source.equals(replacementFar) && target.equals(handoff)) {
                return Optional.of(route(source, target, 30.0, 300.0));
            }
            if (source.equals(handoff) && target.equals(dropoff)) {
                return Optional.of(route(source, target, 40.0, 400.0));
            }
            return Optional.of(route(source, target, 100.0, 1_000.0));
        };
    }

    private Route route(NodeId source, NodeId target, double time, double distance) {
        return new Route(List.of(source, target), time, distance);
    }

    @Test
    void shouldRecoverPickedUpOrderToBestAvailableReplacement() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, failedDriverNode, DriverStatus.OFFLINE));
        drivers.addDriver(new Driver(20L, replacementNear, DriverStatus.AVAILABLE));
        drivers.addDriver(new Driver(30L, replacementFar, DriverStatus.AVAILABLE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        Order order = new Order(100L, new NodeId(6L), dropoff, 1L, OrderStatus.RECOVERY_REQUIRED);
        orders.addOrder(order);

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();
        DriverRecoveryTask task = new DriverRecoveryTask(10L, 100L, handoff, 5_000L);
        queue.enqueue(task);

        RecoveryCandidateSelector.RouteFinder finder = routeFinder();
        RecoveryCandidateSelector selector = new RecoveryCandidateSelector(drivers, finder);
        DriverRecoveryWorker worker = new DriverRecoveryWorker(
                drivers, orders, routes, queue, selector, finder);

        RecoveryAssignment assignment = worker.processNext().orElseThrow();

        assertEquals(new RecoveryAssignment(
                100L,
                20L,
                route(replacementNear, handoff, 10.0, 100.0),
                route(handoff, dropoff, 40.0, 400.0)), assignment);
        assertEquals(OrderStatus.ASSIGNED, orders.getOrder(100L).orElseThrow().status());
        assertEquals(20L, orders.getAssignedDriverId(100L).orElseThrow());
        assertEquals(DriverStatus.BUSY, drivers.getDriver(20L).orElseThrow().status());
        assertTrue(queue.size() == 0);

        DriverRoutePlan plan = routes.getPlan(20L).orElseThrow();
        assertEquals(List.of(orders.getOrder(100L).orElseThrow()), plan.activeOrders());
        assertEquals(
                List.of(
                        new RouteStop(100L, RouteStopType.HANDOFF, handoff),
                        new RouteStop(100L, RouteStopType.DROPOFF, dropoff)),
                plan.stops());
    }

    @Test
    void shouldRequeueWhenNoReplacementDriverIsAvailable() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, failedDriverNode, DriverStatus.OFFLINE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(new Order(100L, new NodeId(6L), dropoff, 1L, OrderStatus.RECOVERY_REQUIRED));

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();
        queue.enqueue(new DriverRecoveryTask(10L, 100L, handoff, 5_000L));

        RecoveryCandidateSelector.RouteFinder finder = routeFinder();
        RecoveryCandidateSelector selector = new RecoveryCandidateSelector(drivers, finder);
        DriverRecoveryWorker worker = new DriverRecoveryWorker(
                drivers, orders, routes, queue, selector, finder);

        assertTrue(worker.processNext().isEmpty());
        assertEquals(1, queue.size());
        assertEquals(OrderStatus.RECOVERY_REQUIRED, orders.getOrder(100L).orElseThrow().status());
    }

    @Test
    void shouldContinueBatchWhenEarlierTaskIsTemporarilyBlocked() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, failedDriverNode, DriverStatus.OFFLINE));
        drivers.addDriver(new Driver(20L, replacementNear, DriverStatus.AVAILABLE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(new Order(100L, new NodeId(6L), dropoff, 1L, OrderStatus.RECOVERY_REQUIRED));
        orders.addOrder(new Order(200L, new NodeId(6L), dropoff, 2L, OrderStatus.RECOVERY_REQUIRED));

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();
        queue.enqueue(new DriverRecoveryTask(10L, 100L, blockedHandoff, 5_000L));
        queue.enqueue(new DriverRecoveryTask(10L, 200L, handoff, 5_001L));

        RecoveryCandidateSelector.RouteFinder finder = (source, target) -> {
            if (source.equals(blockedHandoff)) {
                return Optional.empty();
            }
            return routeFinder().findRoute(source, target);
        };
        RecoveryCandidateSelector selector = new RecoveryCandidateSelector(drivers, finder);
        DriverRecoveryWorker worker = new DriverRecoveryWorker(
                drivers, orders, routes, queue, selector, finder);

        List<RecoveryAssignment> assignments = worker.processBatch(2);

        assertEquals(1, assignments.size());
        assertEquals(200L, assignments.get(0).orderId());
        assertEquals(OrderStatus.RECOVERY_REQUIRED, orders.getOrder(100L).orElseThrow().status());
        assertEquals(20L, orders.getAssignedDriverId(200L).orElseThrow());
        assertEquals(1, queue.size());
    }

    @Test
    void shouldDiscardStaleRecoveryTaskAfterOrderCancellation() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(20L, replacementNear, DriverStatus.AVAILABLE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(new Order(100L, new NodeId(6L), dropoff, 1L, OrderStatus.RECOVERY_REQUIRED));
        assertTrue(orders.tryCancel(100L));

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();
        queue.enqueue(new DriverRecoveryTask(10L, 100L, handoff, 5_000L));

        RecoveryCandidateSelector.RouteFinder finder = routeFinder();
        RecoveryCandidateSelector selector = new RecoveryCandidateSelector(drivers, finder);
        DriverRecoveryWorker worker = new DriverRecoveryWorker(
                drivers, orders, routes, queue, selector, finder);

        assertTrue(worker.processNext().isEmpty());
        assertEquals(0, queue.size());
        assertEquals(OrderStatus.CANCELLED, orders.getOrder(100L).orElseThrow().status());
        assertTrue(routes.getPlan(20L).isEmpty());
    }
}
