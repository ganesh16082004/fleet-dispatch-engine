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
    private final RoadGraph graph;

    public MapController(RoadGraph graph) {
        this.graph = graph;
    }

    @GetMapping("/geojson")
    public Map<String, Object> geoJson() {
        List<Map<String, Object>> features = new ArrayList<>(graph.edgeCount());
        Map<Long, List<Double>> nodeCoordinates = new LinkedHashMap<>(graph.nodeCount());

        double latitudeSum = 0.0;
        double longitudeSum = 0.0;
        double minLatitude = Double.POSITIVE_INFINITY;
        double maxLatitude = Double.NEGATIVE_INFINITY;
        double minLongitude = Double.POSITIVE_INFINITY;
        double maxLongitude = Double.NEGATIVE_INFINITY;
        int coordinateCount = 0;

        for (RoadNode node : graph.nodes().values()) {
            Location location = node.location();
            nodeCoordinates.put(
                    node.id().value(),
                    List.of(location.longitude(), location.latitude()));
            latitudeSum += location.latitude();
            longitudeSum += location.longitude();
            minLatitude = Math.min(minLatitude, location.latitude());
            maxLatitude = Math.max(maxLatitude, location.latitude());
            minLongitude = Math.min(minLongitude, location.longitude());
            maxLongitude = Math.max(maxLongitude, location.longitude());
            coordinateCount++;
        }

        for (RoadEdge edge : graph.nodes().values().stream()
                .flatMap(node -> graph.outgoing(node.id()).stream())
                .toList()) {
            RoadNode from = graph.node(edge.from());
            RoadNode to = graph.node(edge.to());
            if (from == null || to == null) {
                continue;
            }

            Map<String, Object> geometry = Map.of(
                    "type", "LineString",
                    "coordinates", List.of(
                            List.of(from.location().longitude(), from.location().latitude()),
                            List.of(to.location().longitude(), to.location().latitude())));

            Map<String, Object> properties = Map.of(
                    "from", edge.from().value(),
                    "to", edge.to().value(),
                    "distanceMeters", edge.distanceMeters(),
                    "travelTimeSeconds", edge.travelTimeSeconds());

            features.add(Map.of(
                    "type", "Feature",
                    "geometry", geometry,
                    "properties", properties));
        }

        double centerLatitude = coordinateCount == 0 ? 12.9716 : latitudeSum / coordinateCount;
        double centerLongitude = coordinateCount == 0 ? 77.5946 : longitudeSum / coordinateCount;
        List<Double> bounds = coordinateCount == 0
                ? List.of(77.5946, 12.9716, 77.5946, 12.9716)
                : List.of(minLongitude, minLatitude, maxLongitude, maxLatitude);

        Map<String, Object> roads = Map.of(
                "type", "FeatureCollection",
                "features", features);

        return Map.of(
                "roads", roads,
                "nodes", nodeCoordinates,
                "center", List.of(centerLatitude, centerLongitude),
                "bounds", bounds,
                "nodeCount", graph.nodeCount(),
                "edgeCount", graph.edgeCount());
    }
}
