package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchEngineTest {
    private final NodeId driverA = new NodeId(1L);
    private final NodeId driverB = new NodeId(2L);
    private final NodeId pickup = new NodeId(3L);
    private final NodeId dropoff = new NodeId(4L);

    private RoadGraph graph() {
        return new RoadGraph(
                Map.of(
                        driverA, new RoadNode(driverA, new Location(12.9716, 77.5946)),
                        driverB, new RoadNode(driverB, new Location(12.9717, 77.5947)),
                        pickup, new RoadNode(pickup, new Location(12.9720, 77.5950)),
                        dropoff, new RoadNode(dropoff, new Location(12.9725, 77.5955))),
                List.of());
    }

    private Order order() {
        return new Order(100L, pickup, dropoff, 1L, OrderStatus.CREATED);
    }

    @Test
    void shouldChooseDriverWithFasterRoute() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(10L, driverA, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(20L, driverB, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph());
        Router router = (source, target) -> {
            if (source.equals(driverA)) {
                return new Route(List.of(source, target), 12.0, 100.0);
            }
            return new Route(List.of(source, target), 7.0, 200.0);
        };
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(order());

        DispatchEngine engine = new DispatchEngine(selector, store, orders, router, 500.0, 10);

        Optional<DispatchAssignment> result = engine.dispatch(order());

        assertTrue(result.isPresent());
        assertEquals(20L, result.orElseThrow().driverId());
        assertEquals(driverB, store.getDriver(20L).orElseThrow().currentNode());
        assertEquals(DriverStatus.BUSY, store.getDriver(20L).orElseThrow().status());
        assertEquals(7.0, result.orElseThrow().driverToPickupRoute().totalTravelTimeSeconds());
    }

    @Test
    void shouldPreferNearbyDriverWhenRoutesTie() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(10L, driverA, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(20L, driverB, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph());
        Router router = (source, target) -> {
            if (source.equals(driverA)) {
                return new Route(List.of(source, target), 7.0, 100.0);
            }
            return new Route(List.of(source, target), 7.0, 100.0);
        };
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(order());
        DispatchEngine engine = new DispatchEngine(selector, store, orders, router, 500.0, 10);

        Optional<DispatchAssignment> result = engine.dispatch(order());

        assertTrue(result.isPresent());
        assertEquals(10L, result.orElseThrow().driverId());
    }

    @Test
    void shouldReleaseDriverWhenOrderClaimFails() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, driverA, DriverStatus.AVAILABLE));

        Order order = order();
        OrderStateStore rejectingOrders = new OrderStateStore() {
            private final InMemoryOrderStateStore delegate = new InMemoryOrderStateStore();
            {
                delegate.addOrder(order);
            }

            @Override
            public void addOrder(Order value) { delegate.addOrder(value); }

            @Override
            public Optional<Order> getOrder(long orderId) { return delegate.getOrder(orderId); }

            @Override
            public boolean tryAssign(long orderId, long driverId) { return false; }

            @Override
            public boolean tryTransition(
                    long orderId,
                    OrderStatus expectedStatus,
                    OrderStatus newStatus) {
                return delegate.tryTransition(orderId, expectedStatus, newStatus);
            }

            @Override
            public boolean tryCancel(long orderId) { return delegate.tryCancel(orderId); }

            @Override
            public boolean tryRequeue(long orderId, long driverId) {
                return delegate.tryRequeue(orderId, driverId);
            }

            @Override
            public boolean tryOffer(long orderId, long driverId) { return delegate.tryOffer(orderId, driverId); }

            @Override
            public boolean tryAcceptOffer(long orderId, long driverId) {
                return delegate.tryAcceptOffer(orderId, driverId);
            }

            @Override
            public boolean tryRejectOffer(long orderId, long driverId) {
                return delegate.tryRejectOffer(orderId, driverId);
            }

            @Override
            public boolean tryExpireOffer(long orderId, long driverId) {
                return delegate.tryExpireOffer(orderId, driverId);
            }

            @Override
            public OptionalLong getAssignedDriverId(long orderId) { return delegate.getAssignedDriverId(orderId); }

            @Override
            public int size() { return delegate.size(); }
        };

        CandidateSelector selector = new CandidateSelector(drivers, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 2.0, 10.0);
        DispatchEngine engine = new DispatchEngine(selector, drivers, rejectingOrders, router, 500.0, 10);

        assertTrue(engine.dispatch(order).isEmpty());
        assertEquals(DriverStatus.AVAILABLE, drivers.getDriver(10L).orElseThrow().status());
        assertEquals(OrderStatus.CREATED, rejectingOrders.getOrder(100L).orElseThrow().status());
    }

    @Test
    void shouldBlockRouteConsolidationWhenDispatchSlaIsTooStrict() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        Driver driver = new Driver(10L, driverA, DriverStatus.BUSY);
        drivers.addDriver(driver);

        Order existing = new Order(200L, driverB, dropoff, 1L, OrderStatus.CREATED);
        Order incoming = order();
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(existing);
        orders.addOrder(incoming);
        assertTrue(orders.tryAssign(existing.id(), driver.id()));
        existing = orders.getOrder(existing.id()).orElseThrow();

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(existing));

        CandidateSelector selector = new CandidateSelector(drivers, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 10.0, 100.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                500.0,
                10,
                500.0,
                2.0,
                routes,
                new RouteInsertionEngine(router, 10_000.0, 10_000.0));

        DeliveryConstraints strict = new DeliveryConstraints(0.0, 0.0);

        assertTrue(engine.dispatch(incoming, strict).isEmpty());
        assertEquals(OrderStatus.CREATED, orders.getOrder(incoming.id()).orElseThrow().status());
        assertEquals(List.of(existing), routes.getPlan(10L).orElseThrow().activeOrders());
    }

    @Test
    void shouldAllowRouteConsolidationWhenDispatchSlaIsLoose() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        Driver driver = new Driver(10L, driverA, DriverStatus.BUSY);
        drivers.addDriver(driver);

        Order existing = new Order(200L, driverB, dropoff, 1L, OrderStatus.CREATED);
        Order incoming = order();
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(existing);
        orders.addOrder(incoming);
        assertTrue(orders.tryAssign(existing.id(), driver.id()));
        existing = orders.getOrder(existing.id()).orElseThrow();

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(existing));

        CandidateSelector selector = new CandidateSelector(drivers, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 10.0, 100.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                500.0,
                10,
                500.0,
                2.0,
                routes,
                new RouteInsertionEngine(router, 10_000.0, 10_000.0));

        DeliveryConstraints loose = new DeliveryConstraints(10_000.0, 10_000.0);

        Optional<DispatchAssignment> result = engine.dispatch(incoming, loose);

        assertTrue(result.isPresent());
        assertEquals(10L, result.orElseThrow().driverId());
        assertEquals(OrderStatus.ASSIGNED, orders.getOrder(incoming.id()).orElseThrow().status());
        assertEquals(2, routes.getPlan(10L).orElseThrow().activeDeliveryCount());
    }

    @Test
    void shouldCancelAssignedOrderAndFreeDriverWhenRouteBecomesEmpty() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        Driver driver = new Driver(10L, driverA, DriverStatus.BUSY);
        drivers.addDriver(driver);

        Order assigned = new Order(100L, pickup, dropoff, 1L, OrderStatus.CREATED);
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(assigned);
        assertTrue(orders.tryAssign(100L, 10L));
        assigned = orders.getOrder(100L).orElseThrow();
        assertEquals(10L, orders.getAssignedDriverId(100L).orElseThrow());

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(assigned));

        CandidateSelector selector = new CandidateSelector(drivers, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 2.0, 10.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                500.0,
                10,
                500.0,
                2.0,
                routes,
                new RouteInsertionEngine(router, 10_000.0, 10_000.0));

        assertTrue(engine.cancelOrder(100L));
        assertEquals(OrderStatus.CANCELLED, orders.getOrder(100L).orElseThrow().status());
        assertTrue(orders.getAssignedDriverId(100L).isEmpty());
        assertEquals(DriverStatus.AVAILABLE, drivers.getDriver(10L).orElseThrow().status());
        assertTrue(routes.getPlan(10L).isEmpty());
    }

    @Test
    void shouldReassignOrdersWhenDriverFails() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, driverA, DriverStatus.BUSY));
        drivers.addDriver(new Driver(20L, driverB, DriverStatus.AVAILABLE));

        Order assigned = new Order(100L, pickup, dropoff, 1L, OrderStatus.CREATED);
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        orders.addOrder(assigned);
        assertTrue(orders.tryAssign(100L, 10L));
        assigned = orders.getOrder(100L).orElseThrow();
        assertEquals(10L, orders.getAssignedDriverId(100L).orElseThrow());

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(assigned));

        CandidateSelector selector = new CandidateSelector(drivers, graph());
        Router router = (source, target) -> new Route(List.of(source, target),
                source.equals(driverB) ? 5.0 : 20.0,
                100.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                500.0,
                10,
                500.0,
                2.0,
                routes,
                new RouteInsertionEngine(router, 10_000.0, 10_000.0));

        List<DispatchAssignment> assignments = engine.reassignDriver(10L);

        assertEquals(1, assignments.size());
        assertEquals(20L, assignments.get(0).driverId());
        assertEquals(OrderStatus.ASSIGNED, orders.getOrder(100L).orElseThrow().status());
        assertEquals(20L, orders.getAssignedDriverId(100L).orElseThrow());
        assertEquals(DriverStatus.OFFLINE, drivers.getDriver(10L).orElseThrow().status());
        assertTrue(routes.getPlan(10L).isEmpty());
        assertEquals(DriverStatus.BUSY, drivers.getDriver(20L).orElseThrow().status());
        assertEquals(100L, routes.getPlan(20L).orElseThrow().activeOrders().get(0).id());
    }
}
