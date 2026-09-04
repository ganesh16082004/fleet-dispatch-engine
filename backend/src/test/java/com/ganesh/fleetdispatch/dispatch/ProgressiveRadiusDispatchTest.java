package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveRadiusDispatchTest {

    @Test
    void expandsSearchRadiusWhenInitialRadiusHasNoDriver() {
        Location driverLocation = new Location(12.9716, 77.5946);
        Location pickupLocation = new Location(12.9851, 77.5946);

        RoadGraph graph = new RoadGraph(
                new HashMap<>(Map.of(
                        new NodeId(1L), new RoadNode(new NodeId(1L), driverLocation),
                        new NodeId(2L), new RoadNode(new NodeId(2L), pickupLocation))),
                List.of());

        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore(graph);
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        drivers.addDriver(new Driver(1L, new NodeId(1L), DriverStatus.AVAILABLE));

        DispatchEngine engine = new DispatchEngine(
                new CandidateSelector(drivers, graph),
                drivers,
                orders,
                flatRouter(),
                1_000.0,
                10);

        Order order = new Order(
                10L,
                new NodeId(2L),
                new NodeId(2L),
                10L,
                OrderStatus.CREATED);
        orders.addOrder(order);

        DispatchAssignment assignment = engine.dispatch(order).orElseThrow();

        assertEquals(1L, assignment.driverId());
        assertTrue(assignment.driverToPickupRoute().totalTravelTimeSeconds() > 0.0);
    }

    private static Router flatRouter() {
        return (source, target) -> source.equals(target)
                ? new Route(List.of(source), 0.0, 0.0)
                : new Route(List.of(source, target), 10.0, 100.0);
    }
}
