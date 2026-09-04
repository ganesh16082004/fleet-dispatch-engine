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

import static org.junit.jupiter.api.Assertions.*;

class DriverFailureDetectorTest {
    private final NodeId failedNode = new NodeId(1L);
    private final NodeId replacementNode = new NodeId(2L);
    private final NodeId pickupNode = new NodeId(3L);
    private final NodeId dropoffNode = new NodeId(4L);

    private RoadGraph graph() {
        return new RoadGraph(
                Map.of(
                        failedNode, new RoadNode(failedNode, new Location(12.9716, 77.5946)),
                        replacementNode, new RoadNode(replacementNode, new Location(12.9717, 77.5947)),
                        pickupNode, new RoadNode(pickupNode, new Location(12.9720, 77.5950)),
                        dropoffNode, new RoadNode(dropoffNode, new Location(12.9725, 77.5955))),
                List.of());
    }

    @Test
    void shouldDetectStaleDriverAndReassignAssignedOrder() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, failedNode, DriverStatus.BUSY));
        drivers.addDriver(new Driver(20L, replacementNode, DriverStatus.AVAILABLE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        Order order = new Order(100L, pickupNode, dropoffNode, 1L, OrderStatus.CREATED);
        orders.addOrder(order);
        assertTrue(orders.tryAssign(100L, 10L));
        order = orders.getOrder(100L).orElseThrow();

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        routes.putPlan(10L, DriverRoutePlan.single(order));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        heartbeats.recordHeartbeat(10L, 1_000L);
        heartbeats.recordHeartbeat(20L, 1_000L);

        CandidateSelector selector = new CandidateSelector(drivers, graph());
        Router router = (source, target) -> new Route(List.of(source, target), 5.0, 100.0);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                500.0,
                10,
                2_000.0,
                2.0,
                routes,
                new RouteInsertionEngine(router, 10_000.0, 10_000.0));

        PickedUpOrderRecoveryService recovery = new PickedUpOrderRecoveryService(
                drivers,
                orders,
                routes,
                new InMemoryDriverRecoveryQueue());
        DriverFailureDetector detector = new DriverFailureDetector(
                drivers,
                heartbeats,
                recovery,
                engine,
                5_000L);

        List<DriverFailureDetection> detections = detector.detect(6_001L);

        assertEquals(1, detections.size());
        assertEquals(10L, detections.get(0).driverId());
        assertEquals(0, detections.get(0).pickedUpOrdersQueued());
        assertEquals(1, detections.get(0).assignedOrdersReassigned());
        assertEquals(DriverStatus.OFFLINE, drivers.getDriver(10L).orElseThrow().status());
        assertEquals(20L, orders.getAssignedDriverId(100L).orElseThrow());
        assertEquals(DriverStatus.BUSY, drivers.getDriver(20L).orElseThrow().status());
    }

    @Test
    void shouldNotDetectDriverBeforeHeartbeatTimeout() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, failedNode, DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        heartbeats.recordHeartbeat(10L, 1_000L);

        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        Router router = (source, target) -> new Route(List.of(source, target), 1.0, 1.0);
        DispatchEngine engine = new DispatchEngine(
                new CandidateSelector(drivers, graph()),
                drivers,
                new InMemoryOrderStateStore(),
                router,
                500.0,
                10);
        PickedUpOrderRecoveryService recovery = new PickedUpOrderRecoveryService(
                drivers,
                new InMemoryOrderStateStore(),
                routes,
                new InMemoryDriverRecoveryQueue());
        DriverFailureDetector detector = new DriverFailureDetector(
                drivers,
                heartbeats,
                recovery,
                engine,
                5_000L);

        assertTrue(detector.detect(5_999L).isEmpty());
        assertEquals(DriverStatus.AVAILABLE, drivers.getDriver(10L).orElseThrow().status());
    }

    @Test
    void shouldDetectOfflineOnlyOnceUntilDriverIsExplicitlyRegisteredAgain() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, failedNode, DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        heartbeats.recordHeartbeat(10L, 1_000L);
        InMemoryDriverRouteStore routes = new InMemoryDriverRouteStore();
        InMemoryDriverRecoveryQueue queue = new InMemoryDriverRecoveryQueue();
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        DispatchEngine engine = new DispatchEngine(
                new CandidateSelector(drivers, graph()),
                drivers,
                orders,
                (source, target) -> new Route(List.of(source, target), 1.0, 1.0),
                500.0,
                10);
        PickedUpOrderRecoveryService recovery = new PickedUpOrderRecoveryService(
                drivers, orders, routes, queue);
        DriverFailureDetector detector = new DriverFailureDetector(
                drivers, heartbeats, recovery, engine, 5_000L);

        assertEquals(1, detector.detect(6_000L).size());
        assertTrue(detector.detect(7_000L).isEmpty());
        assertEquals(DriverStatus.OFFLINE, drivers.getDriver(10L).orElseThrow().status());
    }
}
