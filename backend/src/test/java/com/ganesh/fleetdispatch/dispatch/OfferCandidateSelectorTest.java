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

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfferCandidateSelectorTest {
    private static final NodeId DRIVER_NEAR = new NodeId(1L);
    private static final NodeId DRIVER_FAR = new NodeId(2L);
    private static final NodeId PICKUP = new NodeId(3L);

    @Test
    void selectsFastestRouteEvenWhenDriverIsFartherAway() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(1L, DRIVER_NEAR, DriverStatus.AVAILABLE));
        drivers.addDriver(new Driver(2L, DRIVER_FAR, DriverStatus.AVAILABLE));

        RoadGraph graph = new RoadGraph(
                Map.of(
                        DRIVER_NEAR, new RoadNode(DRIVER_NEAR, new Location(12.9716, 77.5946)),
                        DRIVER_FAR, new RoadNode(DRIVER_FAR, new Location(12.9740, 77.5970)),
                        PICKUP, new RoadNode(PICKUP, new Location(12.9717, 77.5947))),
                List.of());
        CandidateSelector selector = new CandidateSelector(drivers, graph);
        Router router = (source, target) -> source.equals(DRIVER_NEAR)
                ? new Route(List.of(source, target), 20.0, 100.0)
                : new Route(List.of(source, target), 8.0, 180.0);

        Order order = new Order(100L, PICKUP, PICKUP, 1L, OrderStatus.CREATED);
        OfferCandidateSelector offerSelector = new OfferCandidateSelector(selector, router, 10);

        List<OfferCandidate> candidates = offerSelector.select(order, 2_000.0);

        assertEquals(2, candidates.size());
        assertEquals(2L, candidates.get(0).driver().id());
        assertEquals(8.0, candidates.get(0).incrementalTravelTimeSeconds());
        assertEquals(180.0, candidates.get(0).incrementalDistanceMeters());
    }

    @Test
    void breaksEqualTravelTimeUsingIncrementalDistanceThenDriverId() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, DRIVER_NEAR, DriverStatus.AVAILABLE));
        drivers.addDriver(new Driver(20L, DRIVER_FAR, DriverStatus.AVAILABLE));

        RoadGraph graph = new RoadGraph(
                Map.of(
                        DRIVER_NEAR, new RoadNode(DRIVER_NEAR, new Location(12.9716, 77.5946)),
                        DRIVER_FAR, new RoadNode(DRIVER_FAR, new Location(12.9740, 77.5970)),
                        PICKUP, new RoadNode(PICKUP, new Location(12.9717, 77.5947))),
                List.of());
        CandidateSelector selector = new CandidateSelector(drivers, graph);
        Router router = (source, target) -> source.equals(DRIVER_NEAR)
                ? new Route(List.of(source, target), 10.0, 90.0)
                : new Route(List.of(source, target), 10.0, 120.0);

        Order order = new Order(101L, PICKUP, PICKUP, 1L, OrderStatus.CREATED);
        OfferCandidateSelector offerSelector = new OfferCandidateSelector(selector, router, 10);

        List<OfferCandidate> candidates = offerSelector.select(order, 2_000.0);

        assertEquals(10L, candidates.get(0).driver().id());
    }
}
