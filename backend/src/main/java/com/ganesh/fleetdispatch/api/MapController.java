package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.RoadEdge;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {
    private static final int DASHBOARD_EDGE_LIMIT = 6000;

    private final RoadGraph graph;
    private final Map<String, Object> metadata;
    private final Map<String, Object> dashboardGraph;

    public MapController(RoadGraph graph) {
        this.graph = graph;
        this.metadata = buildMetadata(graph);
        this.dashboardGraph = buildDashboardGraph(graph, metadata);
    }

    /**
     * Lightweight endpoint for the dashboard. It exposes the exact boundary and
     * graph counts without serializing 1M+ road edges on every refresh.
     */
    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Dashboard graph payload. The routing engine keeps the complete graph in
     * memory; the browser receives a deterministic visual sample derived from
     * that exact graph so Leaflet does not need to render 1.2M vector features.
     */
    @GetMapping("/geojson")
    public Map<String, Object> geoJson() {
        return dashboardGraph;
    }

    private static Map<String, Object> buildDashboardGraph(
            RoadGraph graph,
            Map<String, Object> metadata) {
        List<Map<String, Object>> features = new ArrayList<>(DASHBOARD_EDGE_LIMIT);
        Map<Long, List<Double>> nodeCoordinates = new LinkedHashMap<>();
        int totalEdges = graph.edgeCount();
        int stride = Math.max(1, (int) Math.ceil((double) totalEdges / DASHBOARD_EDGE_LIMIT));
        int edgeIndex = 0;

        outer:
        for (RoadNode node : graph.nodes().values()) {
            for (RoadEdge edge : graph.outgoing(node.id())) {
                if (edgeIndex++ % stride != 0) {
                    continue;
                }

                RoadNode from = graph.node(edge.from());
                RoadNode to = graph.node(edge.to());
                if (from == null || to == null) {
                    continue;
                }

                Location fromLocation = from.location();
                Location toLocation = to.location();
                nodeCoordinates.putIfAbsent(
                        from.id().value(),
                        List.of(fromLocation.longitude(), fromLocation.latitude()));
                nodeCoordinates.putIfAbsent(
                        to.id().value(),
                        List.of(toLocation.longitude(), toLocation.latitude()));

                Map<String, Object> geometry = Map.of(
                        "type", "LineString",
                        "coordinates", List.of(
                                List.of(fromLocation.longitude(), fromLocation.latitude()),
                                List.of(toLocation.longitude(), toLocation.latitude())));

                Map<String, Object> properties = Map.of(
                        "from", edge.from().value(),
                        "to", edge.to().value(),
                        "distanceMeters", edge.distanceMeters(),
                        "travelTimeSeconds", edge.travelTimeSeconds());

                features.add(Map.of(
                        "type", "Feature",
                        "geometry", geometry,
                        "properties", properties));

                if (features.size() >= DASHBOARD_EDGE_LIMIT) {
                    break outer;
                }
            }
        }

        Map<String, Object> roads = Map.of(
                "type", "FeatureCollection",
                "features", features);

        return Map.of(
                "roads", roads,
                "nodes", nodeCoordinates,
                "center", metadata.get("center"),
                "bounds", metadata.get("bounds"),
                "nodeCount", graph.nodeCount(),
                "edgeCount", graph.edgeCount(),
                "renderedEdgeCount", features.size());
    }

    private static Map<String, Object> buildMetadata(RoadGraph graph) {
        double latitudeSum = 0.0;
        double longitudeSum = 0.0;
        double minLatitude = Double.POSITIVE_INFINITY;
        double maxLatitude = Double.NEGATIVE_INFINITY;
        double minLongitude = Double.POSITIVE_INFINITY;
        double maxLongitude = Double.NEGATIVE_INFINITY;
        int coordinateCount = 0;

        for (RoadNode node : graph.nodes().values()) {
            Location location = node.location();
            latitudeSum += location.latitude();
            longitudeSum += location.longitude();
            minLatitude = Math.min(minLatitude, location.latitude());
            maxLatitude = Math.max(maxLatitude, location.latitude());
            minLongitude = Math.min(minLongitude, location.longitude());
            maxLongitude = Math.max(maxLongitude, location.longitude());
            coordinateCount++;
        }

        double centerLatitude = coordinateCount == 0 ? 12.9716 : latitudeSum / coordinateCount;
        double centerLongitude = coordinateCount == 0 ? 77.5946 : longitudeSum / coordinateCount;
        List<Double> bounds = coordinateCount == 0
                ? List.of(77.5946, 12.9716, 77.5946, 12.9716)
                : List.of(minLongitude, minLatitude, maxLongitude, maxLatitude);

        return Map.of(
                "center", List.of(centerLatitude, centerLongitude),
                "bounds", bounds,
                "nodeCount", graph.nodeCount(),
                "edgeCount", graph.edgeCount());
    }
}
