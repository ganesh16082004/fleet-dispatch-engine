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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchDispatchTest {
    private final NodeId pickupA = new NodeId(1L);
    private final NodeId pickupB = new NodeId(2L);
    private final NodeId dropoff = new NodeId(3L);
    private final NodeId driverNodeA = new NodeId(10L);
    private final NodeId driverNodeB = new NodeId(11L);

    @Test
    void shouldFindGlobalMinimumCostAssignment() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(100L, driverNodeA, DriverStatus.AVAILABLE));
        drivers.addDriver(new Driver(200L, driverNodeB, DriverStatus.AVAILABLE));

        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        Order orderA = new Order(1_000L, pickupA, dropoff, 1L, OrderStatus.CREATED);
        Order orderB = new Order(2_000L, pickupB, dropoff, 2L, OrderStatus.CREATED);
        orders.addOrder(orderA);
        orders.addOrder(orderB);

        Map<NodeId, RoadNode> nodes = Map.of(
                pickupA, new RoadNode(pickupA, new Location(12.9716, 77.5946)),
                pickupB, new RoadNode(pickupB, new Location(12.9717, 77.5947)),
                dropoff, new RoadNode(dropoff, new Location(12.9720, 77.5950)),
                driverNodeA, new RoadNode(driverNodeA, new Location(12.9718, 77.5948)),
                driverNodeB, new RoadNode(driverNodeB, new Location(12.9719, 77.5949))
        );
        RoadGraph graph = new RoadGraph(nodes, List.of(
                new RoadEdge(driverNodeA, pickupA, 10.0, 1.0),
                new RoadEdge(driverNodeB, pickupA, 10.0, 1.0),
                new RoadEdge(driverNodeA, pickupB, 10.0, 1.0),
                new RoadEdge(driverNodeB, pickupB, 10.0, 1.0)
        ));

        CandidateSelector selector = new CandidateSelector(drivers, graph);
        Router router = (source, target) -> {
            if (source.equals(driverNodeA) && target.equals(pickupA)) {
                return new Route(List.of(source, target), 1.0, 1.0);
            }
            if (source.equals(driverNodeB) && target.equals(pickupA)) {
                return new Route(List.of(source, target), 2.0, 2.0);
            }
            if (source.equals(driverNodeA) && target.equals(pickupB)) {
                return new Route(List.of(source, target), 1.0, 1.0);
            }
            if (source.equals(driverNodeB) && target.equals(pickupB)) {
                return new Route(List.of(source, target), 100.0, 100.0);
            }
            throw new IllegalArgumentException("No route");
        };

        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                500.0,
                10);

        List<DispatchAssignment> assignments = engine.dispatchBatch(List.of(orderA, orderB));

        Map<Long, Long> assignmentsByOrder = assignments.stream()
                .collect(Collectors.toMap(DispatchAssignment::orderId, DispatchAssignment::driverId));
        assertEquals(Map.of(
                1_000L, 200L,
                2_000L, 100L
        ), assignmentsByOrder);

        Set<Long> assignedDrivers = assignments.stream()
                .map(DispatchAssignment::driverId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(100L, 200L), assignedDrivers);
        assertEquals(OrderStatus.ASSIGNED, orders.getOrder(1_000L).orElseThrow().status());
        assertEquals(OrderStatus.ASSIGNED, orders.getOrder(2_000L).orElseThrow().status());
        assertEquals(DriverStatus.BUSY, drivers.getDriver(100L).orElseThrow().status());
        assertEquals(DriverStatus.BUSY, drivers.getDriver(200L).orElseThrow().status());
    }
}
