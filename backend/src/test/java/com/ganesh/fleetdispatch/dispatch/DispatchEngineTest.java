package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.routing.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class DispatchEngineTest {

    private final NodeId driverA = new NodeId(1L);
    private final NodeId driverB = new NodeId(2L);
    private final NodeId pickup = new NodeId(3L);
    private final NodeId dropoff = new NodeId(4L);

    private RoadGraph graph() {
        return new RoadGraph(
                Map.of(
                        driverA, new RoadNode(driverA, new Location(12.9716, 77.5946)),
                        driverB, new RoadNode(driverB, new Location(12.9720, 77.5950)),
                        pickup, new RoadNode(pickup, new Location(12.9730, 77.5960)),
                        dropoff, new RoadNode(dropoff, new Location(12.9740, 77.5970))),
                List.of());
    }

    private Order order() {
        return new Order(100L, pickup, dropoff, 1_000L, OrderStatus.CREATED);
    }

    @Test
    void shouldDispatchToNearestDriver() {
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
            public boolean tryAssignRecovery(long orderId, long driverId) {
                return delegate.tryAssignRecovery(orderId, driverId);
            }

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
        Router router = (source, target) -> new Route(List.of(source, target), 600.0, 100.0);
        RouteCandidateSelector routeSelector = new RouteCandidateSelector(drivers, orders, graph(), router, 10);
        RouteInsertionEngine insertionEngine = new RouteInsertionEngine(router, 300.0, 1_800.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                500.0,
                10,
                routes,
                routeSelector,
                insertionEngine);

        assertTrue(engine.dispatch(incoming).isEmpty());
        assertEquals(OrderStatus.CREATED, orders.getOrder(incoming.id()).orElseThrow().status());
        assertEquals(DriverStatus.BUSY, drivers.getDriver(driver.id()).orElseThrow().status());
    }
}
