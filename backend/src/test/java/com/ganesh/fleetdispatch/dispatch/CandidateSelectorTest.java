package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CandidateSelectorTest {

    private final NodeId pickup = new NodeId(1L);
    private final NodeId nearA = new NodeId(2L);
    private final NodeId nearB = new NodeId(3L);
    private final NodeId far = new NodeId(4L);

    private RoadGraph graph() {
        Map<NodeId, RoadNode> nodes = Map.of(
                pickup, new RoadNode(pickup, new Location(12.9716, 77.5946)),
                nearA, new RoadNode(nearA, new Location(12.9717, 77.5947)),
                nearB, new RoadNode(nearB, new Location(12.9718, 77.5948)),
                far, new RoadNode(far, new Location(13.1000, 77.7000))
        );

        return new RoadGraph(nodes, List.of(
                new RoadEdge(pickup, nearA, 10.0, 1.0),
                new RoadEdge(nearA, nearB, 10.0, 1.0),
                new RoadEdge(nearB, far, 10.0, 1.0),
                new RoadEdge(far, pickup, 10.0, 1.0)
        ));
    }

    private Order order() {
        return new Order(100L, pickup, nearB, 1_000L, OrderStatus.CREATED);
    }

    @Test
    void shouldReturnAvailableDriversOrderedByDistance() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        store.addDriver(new Driver(10L, nearB, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(11L, nearA, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph);
        List<DriverCandidate> candidates = selector.select(order(), 500.0, 10);

        assertEquals(2, candidates.size());
        assertEquals(11L, candidates.get(0).driver().id());
        assertEquals(10L, candidates.get(1).driver().id());
        assertTrue(candidates.get(0).distanceMeters() < candidates.get(1).distanceMeters());
    }

    @Test
    void shouldExcludeBusyAndOfflineDrivers() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        store.addDriver(new Driver(10L, nearA, DriverStatus.BUSY));
        store.addDriver(new Driver(11L, nearB, DriverStatus.OFFLINE));
        store.addDriver(new Driver(12L, pickup, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph);
        List<DriverCandidate> candidates = selector.select(order(), 100.0, 10);

        assertEquals(1, candidates.size());
        assertEquals(12L, candidates.get(0).driver().id());
        assertEquals(0.0, candidates.get(0).distanceMeters(), 1e-9);
    }

    @Test
    void shouldRespectRadius() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        store.addDriver(new Driver(10L, nearA, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(11L, far, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph);
        List<DriverCandidate> candidates = selector.select(order(), 100.0, 10);

        assertEquals(1, candidates.size());
        assertEquals(10L, candidates.get(0).driver().id());
    }

    @Test
    void shouldRespectMaximumCandidateCountAndUseDriverIdAsTieBreaker() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        store.addDriver(new Driver(20L, pickup, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(10L, pickup, DriverStatus.AVAILABLE));
        store.addDriver(new Driver(30L, nearA, DriverStatus.AVAILABLE));

        CandidateSelector selector = new CandidateSelector(store, graph);
        List<DriverCandidate> candidates = selector.select(order(), 500.0, 2);

        assertEquals(2, candidates.size());
        assertEquals(10L, candidates.get(0).driver().id());
        assertEquals(20L, candidates.get(1).driver().id());
    }

    @Test
    void shouldStayCorrectAfterDriverMoves() {
        RoadGraph graph = graph();
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        store.addDriver(new Driver(10L, nearA, DriverStatus.AVAILABLE));

        store.updateLocation(10L, far);

        assertTrue(store.getAvailableDriversNear(
                graph.node(pickup).location(), 100.0, 10).isEmpty());

        store.updateLocation(10L, pickup);

        List<Driver> nearby = store.getAvailableDriversNear(
                graph.node(pickup).location(), 100.0, 10);
        assertEquals(List.of(10L), nearby.stream().map(Driver::id).toList());
    }

    @Test
    void shouldRejectInvalidRadius() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        CandidateSelector selector = new CandidateSelector(store, graph());

        assertThrows(IllegalArgumentException.class, () -> selector.select(order(), -1.0, 10));
        assertThrows(IllegalArgumentException.class, () -> selector.select(order(), Double.NaN, 10));
    }

    @Test
    void shouldRejectInvalidCandidateLimit() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        CandidateSelector selector = new CandidateSelector(store, graph());

        assertThrows(IllegalArgumentException.class, () -> selector.select(order(), 100.0, 0));
    }
}
