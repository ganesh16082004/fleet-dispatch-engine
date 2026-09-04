package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteExecutionServiceTest {
    private final NodeId pickup = new NodeId(1L);
    private final NodeId dropoff = new NodeId(2L);
    private final NodeId other = new NodeId(3L);

    private Fixture fixture() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, pickup, DriverStatus.BUSY));
        drivers.addDriver(new Driver(20L, other, DriverStatus.AVAILABLE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        Order order = new Order(100L, pickup, dropoff, 1L, OrderStatus.CREATED);
        orders.addOrder(order);
        assertTrue(orders.tryAssign(100L, 10L));

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(orders.getOrder(100L).orElseThrow()));

        return new Fixture(
                drivers,
                orders,
                routes,
                new RouteExecutionService(drivers, orders, routes));
    }

    @Test
    void shouldMarkAssignedOrderPickedUpAndRemovePickupStop() {
        Fixture f = fixture();

        assertTrue(f.service.markPickedUp(100L, 10L));
        assertEquals(OrderStatus.PICKED_UP, f.orders.getOrder(100L).orElseThrow().status());
        assertEquals(10L, f.orders.getAssignedDriverId(100L).orElseThrow());
        assertEquals(
                List.of(new RouteStop(100L, RouteStopType.DROPOFF, dropoff)),
                f.routes.getPlan(10L).orElseThrow().stops());
        assertEquals(1, f.routes.getPlan(10L).orElseThrow().activeOrders().size());
        assertEquals(DriverStatus.BUSY, f.drivers.getDriver(10L).orElseThrow().status());
    }

    @Test
    void shouldCompletePickedUpOrderAndFreeDriver() {
        Fixture f = fixture();
        assertTrue(f.service.markPickedUp(100L, 10L));

        assertTrue(f.service.completeOrder(100L, 10L));
        assertEquals(OrderStatus.COMPLETED, f.orders.getOrder(100L).orElseThrow().status());
        assertEquals(10L, f.drivers.getDriver(10L).orElseThrow().id());
        assertEquals(DriverStatus.AVAILABLE, f.drivers.getDriver(10L).orElseThrow().status());
        assertTrue(f.routes.getPlan(10L).isEmpty());
    }

    @Test
    void shouldRejectLifecycleOperationFromWrongDriver() {
        Fixture f = fixture();

        assertThrows(
                IllegalStateException.class,
                () -> f.service.markPickedUp(100L, 20L));
        assertEquals(OrderStatus.ASSIGNED, f.orders.getOrder(100L).orElseThrow().status());
        assertEquals(
                List.of(
                        new RouteStop(100L, RouteStopType.PICKUP, pickup),
                        new RouteStop(100L, RouteStopType.DROPOFF, dropoff)),
                f.routes.getPlan(10L).orElseThrow().stops());
    }

    @Test
    void shouldAdvanceCurrentPickupStopOnlyWhenDriverHasArrived() {
        Fixture f = fixture();

        assertFalse(f.service.completeCurrentStop(10L, other));
        assertEquals(OrderStatus.ASSIGNED, f.orders.getOrder(100L).orElseThrow().status());

        assertTrue(f.service.completeCurrentStop(10L, pickup));
        assertEquals(OrderStatus.PICKED_UP, f.orders.getOrder(100L).orElseThrow().status());
        assertEquals(
                new RouteStop(100L, RouteStopType.DROPOFF, dropoff),
                f.service.nextStop(10L).orElseThrow());
    }

    @Test
    void shouldCompleteCurrentDropoffAndRemoveRoute() {
        Fixture f = fixture();
        assertTrue(f.service.completeCurrentStop(10L, pickup));
        assertTrue(f.service.completeCurrentStop(10L, dropoff));

        assertEquals(OrderStatus.COMPLETED, f.orders.getOrder(100L).orElseThrow().status());
        assertTrue(f.service.nextStop(10L).isEmpty());
        assertTrue(f.routes.getPlan(10L).isEmpty());
        assertEquals(DriverStatus.AVAILABLE, f.drivers.getDriver(10L).orElseThrow().status());
    }

    @Test
    void shouldExecuteRecoveryHandoffThenDropoff() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(20L, other, DriverStatus.BUSY));
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        Order order = new Order(200L, pickup, dropoff, 1L, OrderStatus.RECOVERY_REQUIRED);
        orders.addOrder(order);
        assertTrue(orders.tryAssignRecovery(200L, 20L));
        order = orders.getOrder(200L).orElseThrow();

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        NodeId handoff = new NodeId(4L);
        routes.putPlan(
                20L,
                new DriverRoutePlan(
                        List.of(order),
                        List.of(
                                new RouteStop(200L, RouteStopType.HANDOFF, handoff),
                                new RouteStop(200L, RouteStopType.DROPOFF, dropoff))));

        RouteExecutionService service = new RouteExecutionService(drivers, orders, routes);

        assertTrue(service.completeCurrentStop(20L, handoff));
        assertEquals(OrderStatus.PICKED_UP, orders.getOrder(200L).orElseThrow().status());
        assertEquals(
                List.of(new RouteStop(200L, RouteStopType.DROPOFF, dropoff)),
                routes.getPlan(20L).orElseThrow().stops());

        assertTrue(service.completeCurrentStop(20L, dropoff));
        assertEquals(OrderStatus.COMPLETED, orders.getOrder(200L).orElseThrow().status());
        assertTrue(routes.getPlan(20L).isEmpty());
        assertEquals(DriverStatus.AVAILABLE, drivers.getDriver(20L).orElseThrow().status());
    }

    private record Fixture(
            InMemoryDriverStateStore drivers,
            InMemoryOrderStateStore orders,
            InMemoryDriverRouteStore routes,
            RouteExecutionService service) {
    }
}
