package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchOfferServiceTest {
    private static final NodeId DRIVER_NODE = new NodeId(1L);
    private static final NodeId PICKUP = new NodeId(2L);
    private static final NodeId DROPOFF = new NodeId(3L);

    @Test
    void createAcceptTransitionsOrderAndDriver() {
        Fixture fixture = fixture();
        Order order = fixture.order();

        DispatchOffer offer = fixture.service().createOffer(order, 1_000L).orElseThrow();

        assertEquals(OrderStatus.OFFERED, fixture.orders().getOrder(order.id()).orElseThrow().status());
        assertEquals(DriverStatus.BUSY, fixture.drivers().getDriver(7L).orElseThrow().status());
        assertTrue(fixture.service().accept(offer.offerId()));
        assertEquals(DispatchOfferStatus.ACCEPTED,
                fixture.service().get(offer.offerId()).orElseThrow().status());
        assertEquals(OrderStatus.ASSIGNED, fixture.orders().getOrder(order.id()).orElseThrow().status());
        assertEquals(7L, fixture.orders().getAssignedDriverId(order.id()).orElseThrow());
    }

    @Test
    void rejectReleasesDriverAndMakesOrderCreatedAgain() {
        Fixture fixture = fixture();
        DispatchOffer offer = fixture.service().createOffer(fixture.order(), 1_000L).orElseThrow();

        assertTrue(fixture.service().reject(offer.offerId()));
        assertEquals(DispatchOfferStatus.REJECTED,
                fixture.service().get(offer.offerId()).orElseThrow().status());
        assertEquals(OrderStatus.CREATED, fixture.orders().getOrder(500L).orElseThrow().status());
        assertEquals(DriverStatus.AVAILABLE, fixture.drivers().getDriver(7L).orElseThrow().status());
        assertFalse(fixture.service().reject(offer.offerId()));
    }

    @Test
    void expireRequiresTimeToReachDeadline() {
        Fixture fixture = fixture();
        DispatchOffer offer = fixture.service().createOffer(fixture.order(), 1_000L).orElseThrow();

        assertFalse(fixture.service().expire(offer.offerId(), offer.expiresAtMillis() - 1));
        assertTrue(fixture.service().expire(offer.offerId(), offer.expiresAtMillis()));
        assertEquals(DispatchOfferStatus.EXPIRED,
                fixture.service().get(offer.offerId()).orElseThrow().status());
        assertEquals(OrderStatus.CREATED, fixture.orders().getOrder(500L).orElseThrow().status());
        assertEquals(DriverStatus.AVAILABLE, fixture.drivers().getDriver(7L).orElseThrow().status());
        assertFalse(fixture.service().expire(offer.offerId(), offer.expiresAtMillis() + 1));
    }

    @Test
    void acceptedOfferCannotBeRejectedOrExpired() {
        Fixture fixture = fixture();
        DispatchOffer offer = fixture.service().createOffer(fixture.order(), 1_000L).orElseThrow();

        assertTrue(fixture.service().accept(offer.offerId()));
        assertFalse(fixture.service().reject(offer.offerId()));
        assertFalse(fixture.service().expire(offer.offerId(), offer.expiresAtMillis() + 10_000));
    }

    private Fixture fixture() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();

        RoadGraph graph = new RoadGraph(
                Map.of(
                        DRIVER_NODE, new RoadNode(DRIVER_NODE, new Location(12.9716, 77.5946)),
                        PICKUP, new RoadNode(PICKUP, new Location(12.9717, 77.5947)),
                        DROPOFF, new RoadNode(DROPOFF, new Location(12.9720, 77.5950))),
                List.of());

        Driver driver = new Driver(7L, DRIVER_NODE, DriverStatus.AVAILABLE);
        Order order = new Order(500L, PICKUP, DROPOFF, 10L, OrderStatus.CREATED);
        drivers.addDriver(driver);
        orders.addOrder(order);

        CandidateSelector selector = new CandidateSelector(drivers, graph);
        DispatchOfferService.RouterAdapter router =
                (source, target) -> Optional.of(new Route(List.of(source, target), 10.0, 100.0));

        DispatchOfferService service = new DispatchOfferService(
                selector,
                drivers,
                orders,
                routes,
                router,
                new InMemoryDispatchOfferStore(),
                500.0,
                10,
                5_000L);
        return new Fixture(service, drivers, orders, order);
    }

    private record Fixture(
            DispatchOfferService service,
            InMemoryDriverStateStore drivers,
            InMemoryOrderStateStore orders,
            Order order) {
    }
}
