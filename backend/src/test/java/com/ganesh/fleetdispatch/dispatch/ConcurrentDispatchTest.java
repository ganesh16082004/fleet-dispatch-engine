package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentDispatchTest {
    private static final NodeId PICKUP = new NodeId(1L);
    private static final NodeId DROPOFF = new NodeId(2L);
    private static final NodeId DRIVER_NODE = new NodeId(3L);

    @Test
    void shouldPreventDoubleAssignmentUnderConcurrentDispatch() throws Exception {
        int driverCount = 20;
        int orderCount = 100;
        int workerCount = 20;

        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();

        RoadGraph graph = new RoadGraph(
                Map.of(
                        PICKUP, new RoadNode(PICKUP, new Location(12.9716, 77.5946)),
                        DROPOFF, new RoadNode(DROPOFF, new Location(12.9720, 77.5950)),
                        DRIVER_NODE, new RoadNode(DRIVER_NODE, new Location(12.9717, 77.5947))
                ),
                List.of()
        );

        for (int i = 0; i < driverCount; i++) {
            drivers.addDriver(new Driver(1_000L + i, DRIVER_NODE, DriverStatus.AVAILABLE));
        }

        List<Order> pendingOrders = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                    10_000L + i,
                    PICKUP,
                    DROPOFF,
                    2_000L + i,
                    OrderStatus.CREATED
            );
            orders.addOrder(order);
            pendingOrders.add(order);
        }

        CandidateSelector selector = new CandidateSelector(drivers, graph);
        Router router = (source, target) -> new Route(List.of(source, target), 10.0, 10.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                500.0,
                driverCount
        );

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<DispatchAssignment> assignments = new ConcurrentLinkedQueue<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int worker = 0; worker < workerCount; worker++) {
            int offset = worker;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int index = offset; index < pendingOrders.size(); index += workerCount) {
                        engine.dispatch(pendingOrders.get(index)).ifPresent(assignments::add);
                    }
                } catch (Throwable failure) {
                    failures.add(failure);
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), () -> "Concurrent dispatch failures: " + failures);

        assertEquals(driverCount, assignments.size());

        Set<Long> assignedDriverIds = assignments.stream()
                .map(DispatchAssignment::driverId)
                .collect(Collectors.toSet());
        assertEquals(driverCount, assignedDriverIds.size());

        for (DispatchAssignment assignment : assignments) {
            assertEquals(
                    OrderStatus.ASSIGNED,
                    orders.getOrder(assignment.orderId()).orElseThrow().status()
            );
            assertEquals(
                    OptionalLong.of(assignment.driverId()),
                    orders.getAssignedDriverId(assignment.orderId())
            );
            assertEquals(
                    DriverStatus.BUSY,
                    drivers.getDriver(assignment.driverId()).orElseThrow().status()
            );
        }

        long assignedOrders = pendingOrders.stream()
                .filter(order -> orders.getOrder(order.id()).orElseThrow().status() == OrderStatus.ASSIGNED)
                .count();
        assertEquals(driverCount, assignedOrders);
    }
}
