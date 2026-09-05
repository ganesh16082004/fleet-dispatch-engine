package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.dispatch.CandidateSelector;
import com.ganesh.fleetdispatch.dispatch.DispatchEngine;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverStateStore;
import com.ganesh.fleetdispatch.dispatch.InMemoryOrderStateStore;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.graph.CsvRoadNetworkLoader;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.domain.Location;
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

        log.warn("Road-network CSV files not found (nodes={}, edges={}); using demo graph", nodes.toAbsolutePath(), edges.toAbsolutePath());
        return demoGraph();
    }

    @Bean
    public DriverStateStore driverStateStore(RoadGraph graph) {
        return new InMemoryDriverStateStore(graph);
    }

    @Bean
    public OrderStateStore orderStateStore() {
        return new InMemoryOrderStateStore();
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
            Router router) {
        CandidateSelector selector = new CandidateSelector(drivers, graph);
        return new DispatchEngine(selector, drivers, orders, router, 2_000.0, 10);
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
