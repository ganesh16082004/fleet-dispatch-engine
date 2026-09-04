package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Operational fleet and order summary")
public class DashboardController {
    private final DashboardService dashboardService;
    private final RoadGraph roadGraph;

    public DashboardController(DashboardService dashboardService, RoadGraph roadGraph) {
        this.dashboardService = dashboardService;
        this.roadGraph = roadGraph;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get operational dashboard summary")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/graph")
    @Operation(summary = "Get the Bengaluru road graph for map visualization")
    public MapGraphResponse graph() {
        Map<String, List<Double>> nodes = new LinkedHashMap<>();
        double latitudeSum = 0.0;
        double longitudeSum = 0.0;

        for (RoadNode roadNode : roadGraph.nodes().values()) {
            Location location = roadNode.location();
            nodes.put(String.valueOf(roadNode.id().value()), List.of(location.latitude(), location.longitude()));
            latitudeSum += location.latitude();
            longitudeSum += location.longitude();
        }

        List<Map<String, Object>> features = new ArrayList<>();
        for (Map.Entry<NodeId, List<RoadEdge>> entry : roadGraph.nodes().keySet().stream()
                .collect(java.util.stream.Collectors.toMap(id -> id, roadGraph::outgoing,
                        (left, right) -> left, LinkedHashMap::new)).entrySet()) {
            for (RoadEdge edge : entry.getValue()) {
                RoadNode from = roadGraph.node(edge.from());
                RoadNode to = roadGraph.node(edge.to());
                if (from == null || to == null) {
                    continue;
                }

                Map<String, Object> geometry = new LinkedHashMap<>();
                geometry.put("type", "LineString");
                geometry.put("coordinates", List.of(
                        List.of(from.location().longitude(), from.location().latitude()),
                        List.of(to.location().longitude(), to.location().latitude())));

                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("from", edge.from().value());
                properties.put("to", edge.to().value());
                properties.put("distanceMeters", edge.distanceMeters());
                properties.put("travelTimeSeconds", edge.travelTimeSeconds());

                Map<String, Object> feature = new LinkedHashMap<>();
                feature.put("type", "Feature");
                feature.put("geometry", geometry);
                feature.put("properties", properties);
                features.add(feature);
            }
        }

        Map<String, Object> roads = new LinkedHashMap<>();
        roads.put("type", "FeatureCollection");
        roads.put("features", features);

        List<Double> center = roadGraph.nodeCount() == 0
                ? List.of(12.9716, 77.5946)
                : List.of(latitudeSum / roadGraph.nodeCount(), longitudeSum / roadGraph.nodeCount());

        return new MapGraphResponse(roads, nodes, center, roadGraph.nodeCount(), roadGraph.edgeCount());
    }
}
