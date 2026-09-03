package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DispatchEngineTest {
    private final NodeId pickup = new NodeId(1L);
    private final NodeId dropoff = new NodeId(4L);
    private final NodeId driverA = new NodeId(2L);
    private final NodeId driverB = new NodeId(3L);

    private RoadGraph graph() {
        Map<NodeId, RoadNode> nodes = Map.of(
                pickup, new RoadNode(pickup, new Location(12.9716, 77.5946)),
                driverA, new RoadNode(driverA, new Location(12.9717, 77.5947)),
                driverB, new RoadNode(driverB, new Location(12.9718, 77.5948)),
                dropoff, new RoadNode(dropoff, new Location(12.9720, 77.5950))
        );

        return new RoadGraph(nodes, List.of(
                new RoadEdge(pickup, driverA, 10.0, 1.0),
                new RoadEdge(driverA, pickup, 10.0, 1.0),
                new RoadEdge(pickup, driverB, 20.0, 1.0),
                new RoadEdge(driverB, pickup, 20.0, 1.0),
                new RoadEdge(pickup, dropoff, 30.0, 1.0)
        ));
    }

    private Order order() {
        return new Order(100L, pickup, dropoff, 1_000L, OrderStatus.CREATED);
    }

    @Test
    void shouldAssignDriverUsingRoadTravelTime() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(20L, driverB, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(10L, driverA, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph());
        Router router = (source, target) -> {
            if (source.equals(driverA) && target.equals(pickup)) {
                return new Route(List.of(driverA, pickup), 8.0, 10.0);
            }
            if (source.equals(driverB) && target.equals(pickup)) {
                return new Route(List.of(driverB, pickup), 3.0, 20.0);
            }
            throw new IllegalArgumentException("No test route");
        };

        DispatchEngine engine = new DispatchEngine(selector, store, router, 500.0, 10);

        Optional<DispatchAssignment> result = engine.dispatch(order());

        assertTrue(result.isPresent());
        assertEquals(20L, result.orElseThrow().driverId());
        assertEquals(3.0, result.orElseThrow().driverToPickupRoute().totalTravelTimeSeconds());
        assertEquals(DriverStatus.BUSY, store.getDriver(20L).orElseThrow().status());
        assertEquals(DriverStatus.AVAILABLE, store.getDriver(10L).orElseThrow().status());
    }

    @Test
    void shouldReturnEmptyWhenNoDriverIsAvailable() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(10L, driverA, DriverStatus.BUSY));

        CandidateSelector selector = new CandidateSelector(store, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 10.0, 10.0);
        DispatchEngine engine = new DispatchEngine(selector, store, router, 500.0, 10);

        assertTrue(engine.dispatch(order()).isEmpty());
    }

    @Test
    void shouldRejectNonCreatedOrders() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(10L, driverA, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 10.0, 10.0);
        DispatchEngine engine = new DispatchEngine(selector, store, router, 500.0, 10);

        Order delivered = new Order(101L, pickup, dropoff, 1_000L, OrderStatus.DELIVERED);

        assertThrows(IllegalArgumentException.class, () -> engine.dispatch(delivered));
    }

    @Test
    void shouldNotClaimDriverWhoseLocationChangedAfterCandidateDiscovery() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(10L, driverA, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 10.0, 10.0);
        DispatchEngine engine = new DispatchEngine(selector, store, router, 500.0, 10);

        store.updateLocation(10L, driverB);

        Optional<DispatchAssignment> result = engine.dispatch(order());

        assertTrue(result.isPresent());
        assertEquals(10L, result.orElseThrow().driverId());
        assertEquals(DriverStatus.BUSY, store.getDriver(10L).orElseThrow().status());
        assertEquals(driverB, store.getDriver(10L).orElseThrow().currentNode());
    }
}
