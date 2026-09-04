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

class RouteConsolidationDispatchTest {

    @Test
    void reusesBusyDriverForSecondAndThirdOrders() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore(graph);
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();
        drivers.addDriver(new Driver(1L, new NodeId(1L), DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(drivers, graph);
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                flatRouter(),
                1_000.0,
                10);

        Order first = order(100L, 2L, 3L);
        Order second = order(101L, 4L, 5L);
        Order third = order(102L, 6L, 7L);
        orders.addOrder(first);
        orders.addOrder(second);
        orders.addOrder(third);

        DispatchAssignment firstAssignment = engine.dispatch(first).orElseThrow();
        DispatchAssignment secondAssignment = engine.dispatch(second).orElseThrow();
        DispatchAssignment thirdAssignment = engine.dispatch(third).orElseThrow();

        assertEquals(1L, firstAssignment.driverId());
        assertEquals(1L, secondAssignment.driverId());
        assertEquals(1L, thirdAssignment.driverId());
    }

    @Test
    void rejectsFourthOrderWhenDriverAlreadyHasThreeActiveDeliveries() {
        RoadGraph graph = graph();
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

        for (int i = 0; i < 4; i++) {
            Order order = order(200L + i, 2L + i * 2L, 3L + i * 2L);
            orders.addOrder(order);
            if (i < 3) {
                assertTrue(engine.dispatch(order).isPresent());
            } else {
                assertTrue(engine.dispatch(order).isEmpty());
            }
        }
    }

    private static Order order(long id, long pickup, long dropoff) {
        return new Order(id, new NodeId(pickup), new NodeId(dropoff), id, OrderStatus.CREATED);
    }

    private static RoadGraph graph() {
        Map<NodeId, RoadNode> nodes = new HashMap<>();
        Location same = new Location(12.9716, 77.5946);
        for (long id = 1L; id <= 10L; id++) {
            NodeId nodeId = new NodeId(id);
            nodes.put(nodeId, new RoadNode(nodeId, same));
        }
        return new RoadGraph(nodes, List.of());
    }

    private static Router flatRouter() {
        return (source, target) -> source.equals(target)
                ? new Route(List.of(source), 0.0, 0.0)
                : new Route(List.of(source, target), 10.0, 100.0);
    }
}
