package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickedUpOrderRecoveryServiceTest {
    private final NodeId driverNode = new NodeId(1L);
    private final NodeId pickup = new NodeId(2L);
    private final NodeId dropoff = new NodeId(3L);

    private RoadGraph graph() {
        return new RoadGraph(
                Map.of(
                        driverNode, new RoadNode(driverNode, new Location(12.9716, 77.5946)),
                        pickup, new RoadNode(pickup, new Location(12.9720, 77.5950)),
                        dropoff, new RoadNode(dropoff, new Location(12.9730, 77.5960))),
                List.of());
    }

    @Test
    void shouldQueuePickedUpOrderForRecovery() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, driverNode, DriverStatus.BUSY));

        Order order = new Order(100L, pickup, dropoff, 1L, OrderStatus.PICKED_UP);
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(order);

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(order));

        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();
        PickedUpOrderRecoveryService service = new PickedUpOrderRecoveryService(
                drivers,
                orders,
                routes,
                queue);

        List<DriverRecoveryTask> tasks = service.queuePickedUpOrders(10L, 5_000L);

        assertEquals(1, tasks.size());
        assertEquals(new DriverRecoveryTask(10L, 100L, driverNode, 5_000L), tasks.get(0));
        assertEquals(1, queue.size());
        assertEquals(OrderStatus.RECOVERY_REQUIRED, orders.getOrder(100L).orElseThrow().status());
        assertTrue(orders.getAssignedDriverId(100L).isEmpty());
        assertEquals(DriverStatus.OFFLINE, drivers.getDriver(10L).orElseThrow().status());
        assertTrue(routes.getPlan(10L).isEmpty());
    }

    @Test
    void shouldLeaveAssignedOrderForNormalReassignment() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, driverNode, DriverStatus.BUSY));

        Order order = new Order(100L, pickup, dropoff, 1L, OrderStatus.ASSIGNED);
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(order);
        assertTrue(orders.tryAssign(100L, 10L));
        order = orders.getOrder(100L).orElseThrow();

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(order));
        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();

        PickedUpOrderRecoveryService service = new PickedUpOrderRecoveryService(
                drivers,
                orders,
                routes,
                queue);

        List<DriverRecoveryTask> tasks = service.queuePickedUpOrders(10L, 5_000L);

        assertTrue(tasks.isEmpty());
        assertEquals(0, queue.size());
        assertEquals(OrderStatus.ASSIGNED, orders.getOrder(100L).orElseThrow().status());
        assertEquals(10L, orders.getAssignedDriverId(100L).orElseThrow());
        assertEquals(DriverStatus.OFFLINE, drivers.getDriver(10L).orElseThrow().status());
    }
}
