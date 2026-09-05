package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.dispatch.CandidateSelector;
import com.ganesh.fleetdispatch.dispatch.DispatchCandidateScorer;
import com.ganesh.fleetdispatch.dispatch.DispatchEngine;
import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverRouteStore;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverStateStore;
import com.ganesh.fleetdispatch.dispatch.InMemoryOrderStateStore;
import com.ganesh.fleetdispatch.dispatch.Order;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.dispatch.OrderStatus;
import com.ganesh.fleetdispatch.dispatch.RouteInsertionEngine;
import com.ganesh.fleetdispatch.dispatch.TravelTimeDispatchCandidateScorer;
import com.ganesh.fleetdispatch.graph.CsvRoadNetworkLoader;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import com.ganesh.fleetdispatch.persistence.OrderDocument;
import com.ganesh.fleetdispatch.persistence.OrderRepository;
import com.ganesh.fleetdispatch.routing.AStarRouter;
import com.ganesh.fleetdispatch.routing.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class FleetEngineConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FleetEngineConfiguration.class);

    @Bean
    public RoadGraph roadGraph() {
        String nodesFile = configuredPath("FLEET_NODES_FILE", "data/processed/bengaluru/nodes.csv");
        String edgesFile = configuredPath("FLEET_EDGES_FILE", "data/processed/bengaluru/edges.csv");

        Path nodes = Path.of(nodesFile);
        Path edges = Path.of(edgesFile);
        if (Files.isRegularFile(nodes) && Files.isRegularFile(edges)) {
            RoadGraph graph = new CsvRoadNetworkLoader(nodes, edges).load();
            log.info("Loaded road network from CSV: nodesFile={}, edgesFile={}, nodes={}, edges={}",
                    nodes.toAbsolutePath(), edges.toAbsolutePath(), graph.nodeCount(), graph.edgeCount());
            return graph;
        }

        if (!booleanProperty("FLEET_ALLOW_DEMO_GRAPH", true)) {
            throw new IllegalStateException(
                    "Road-network CSV files are required but were not found. "
                            + "Expected nodes=" + nodes.toAbsolutePath()
                            + " and edges=" + edges.toAbsolutePath()
                            + ". Set FLEET_NODES_FILE/FLEET_EDGES_FILE to the mounted production graph files.");
        }

        log.warn("Road-network CSV files not found (nodes={}, edges={}); using demo graph because FLEET_ALLOW_DEMO_GRAPH=true",
                nodes.toAbsolutePath(), edges.toAbsolutePath());
        return demoGraph();
    }

    @Bean
    public DriverStateStore driverStateStore(
            RoadGraph graph,
            DriverRepository driverRepository) {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore(graph);
        int restored = 0;
        for (DriverDocument document : driverRepository.findAll()) {
            try {
                DriverStatus status = DriverStatus.valueOf(document.status());
                store.addDriver(new Driver(
                        document.id(),
                        new NodeId(document.currentNode()),
                        status));
                restored++;
            } catch (IllegalArgumentException exception) {
                log.warn("Skipping persisted driver {} because its state is invalid: {}", document.id(), exception.getMessage());
            }
        }
        log.info("Restored {} persisted drivers into runtime dispatch state", restored);
        return store;
    }

    @Bean
    public OrderStateStore orderStateStore(OrderRepository orderRepository) {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        int restored = 0;
        for (OrderDocument document : orderRepository.findAll()) {
            try {
                OrderStatus status = OrderStatus.valueOf(document.status());
                store.restoreOrder(
                        new Order(
                                document.id(),
                                new NodeId(document.pickupNode()),
                                new NodeId(document.dropoffNode()),
                                document.requestTimestamp(),
                                status),
                        document.assignedDriverId());
                restored++;
            } catch (IllegalArgumentException exception) {
                log.warn("Skipping persisted order {} because its state is invalid: {}", document.id(), exception.getMessage());
            }
        }
        log.info("Restored {} persisted orders into runtime lifecycle state", restored);
        return store;
    }

    @Bean
    public Router router(RoadGraph graph) {
        return new AStarRouter(graph, 33.33);
    }

    @Bean
    public DispatchEngine dispatchEngine(
            DriverStateStore drivers,
            OrderStateStore orders,
            RoadGraph graph,
            Router router,
            DriverRouteStore driverRouteStore) {
        CandidateSelector selector = new CandidateSelector(drivers, graph);
        DispatchCandidateScorer scorer = new TravelTimeDispatchCandidateScorer();
        return new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                scorer,
                2_000.0,
                10,
                8_000.0,
                2.0,
                driverRouteStore,
                new RouteInsertionEngine(
                        router,
                        300.0,
                        1_800.0));
    }

    private static String configuredPath(String key, String defaultValue) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty.trim();
        }
        String environment = System.getenv(key);
        if (environment != null && !environment.isBlank()) {
            return environment.trim();
        }
        return defaultValue;
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static RoadGraph demoGraph() {
        Map<NodeId, RoadNode> nodes = new HashMap<>();
        List<RoadEdge> edges = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long id = 101L + i;
            nodes.put(new NodeId(id), new RoadNode(
                    new NodeId(id),
                    new Location(12.9716 + i * 0.001, 77.5946 + i * 0.001)));
            if (i > 0) {
                long previous = id - 1;
                edges.add(new RoadEdge(new NodeId(previous), new NodeId(id), 150.0, 18.0));
                edges.add(new RoadEdge(new NodeId(id), new NodeId(previous), 150.0, 18.0));
            }
        }
        return new RoadGraph(nodes, edges);
    }
}
