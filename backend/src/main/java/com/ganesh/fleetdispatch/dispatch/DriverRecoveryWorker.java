package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Consumes picked-up order recovery work and atomically assigns replacement drivers. */
public final class DriverRecoveryWorker {
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final DriverRouteStore driverRouteStore;
    private final DriverRecoveryQueue recoveryQueue;
    private final RecoveryCandidateSelector candidateSelector;
    private final RecoveryCandidateSelector.RouteFinder routeFinder;
    private final ConcurrentHashMap<Long, Object> driverLocks = new ConcurrentHashMap<>();

    public DriverRecoveryWorker(
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRouteStore driverRouteStore,
            DriverRecoveryQueue recoveryQueue,
            RecoveryCandidateSelector candidateSelector,
            RecoveryCandidateSelector.RouteFinder routeFinder) {
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.orderStateStore = Objects.requireNonNull(orderStateStore, "orderStateStore");
        this.driverRouteStore = Objects.requireNonNull(driverRouteStore, "driverRouteStore");
        this.recoveryQueue = Objects.requireNonNull(recoveryQueue, "recoveryQueue");
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.routeFinder = Objects.requireNonNull(routeFinder, "routeFinder");
    }

    /** Processes one queued recovery task. Failed attempts remain in the queue for a later retry. */
    public Optional<RecoveryAssignment> processNext() {
        List<DriverRecoveryTask> tasks = recoveryQueue.drain(1);
        if (tasks.isEmpty()) {
            return Optional.empty();
        }

        DriverRecoveryTask task = tasks.get(0);
        Order order = orderStateStore.getOrder(task.orderId()).orElse(null);
        if (order == null || order.status() != OrderStatus.RECOVERY_REQUIRED) {
            return Optional.empty();
        }

        for (RecoveryCandidate candidate : candidateSelector.select(task, order)) {
            Driver driver = candidate.driver();
            Object lock = driverLocks.computeIfAbsent(driver.id(), ignored -> new Object());
            synchronized (lock) {
                Driver currentDriver = driverStateStore.getDriver(driver.id()).orElse(null);
                if (currentDriver == null || currentDriver.status() != DriverStatus.AVAILABLE) {
                    continue;
                }

                Optional<Route> currentHandoffRoute = routeFinder.findRoute(
                        currentDriver.currentNode(),
                        task.handoffNode());
                if (currentHandoffRoute.isEmpty()) {
                    continue;
                }

                if (!driverStateStore.reserveDriver(driver.id(), currentDriver.currentNode())) {
                    continue;
                }

                if (!orderStateStore.tryAssignRecovery(task.orderId(), driver.id())) {
                    driverStateStore.releaseDriver(driver.id(), currentDriver.currentNode());
                    continue;
                }

                Order assignedOrder = orderStateStore.getOrder(task.orderId()).orElseThrow();
                driverRouteStore.putPlan(
                        driver.id(),
                        new DriverRoutePlan(
                                List.of(assignedOrder),
                                List.of(
                                        new RouteStop(task.orderId(), RouteStopType.HANDOFF, task.handoffNode()),
                                        new RouteStop(task.orderId(), RouteStopType.DROPOFF, assignedOrder.dropoffNode()))));

                return Optional.of(new RecoveryAssignment(
                        task.orderId(),
                        driver.id(),
                        currentHandoffRoute.get(),
                        candidate.handoffToDropoffRoute()));
            }
        }

        Order latest = orderStateStore.getOrder(task.orderId()).orElse(null);
        if (latest != null && latest.status() == OrderStatus.RECOVERY_REQUIRED) {
            recoveryQueue.enqueue(task);
        }
        return Optional.empty();
    }

    /** Processes up to maxItems queued recovery tasks. Tasks without a replacement remain queued. */
    public List<RecoveryAssignment> processBatch(int maxItems) {
        if (maxItems <= 0) {
            throw new IllegalArgumentException("maxItems must be positive");
        }

        java.util.ArrayList<RecoveryAssignment> assignments = new java.util.ArrayList<>();
        for (int i = 0; i < maxItems; i++) {
            Optional<RecoveryAssignment> assignment = processNext();
            if (assignment.isEmpty()) {
                break;
            }
            assignments.add(assignment.get());
        }
        return List.copyOf(assignments);
    }
}
