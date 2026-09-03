package com.ganesh.fleetdispatch.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** In-memory directed road graph used by routing algorithms. */
public final class RoadGraph {
    private final Map<NodeId, RoadNode> nodes;
    private final Map<NodeId, List<RoadEdge>> outgoing;

    public RoadGraph(Map<NodeId, RoadNode> nodes, List<RoadEdge> edges) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");

        Map<NodeId, RoadNode> nodeCopy = new HashMap<>(nodes);
        Map<NodeId, List<RoadEdge>> adjacency = new HashMap<>();
        for (RoadEdge edge : edges) {
            if (!nodeCopy.containsKey(edge.from()) || !nodeCopy.containsKey(edge.to())) {
                throw new IllegalArgumentException("Edge references an unknown node");
            }
            adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        nodeCopy.replaceAll((id, node) -> Objects.requireNonNull(node, "node"));
        adjacency.replaceAll((id, list) -> List.copyOf(list));
        this.nodes = Collections.unmodifiableMap(nodeCopy);
        this.outgoing = Collections.unmodifiableMap(adjacency);
    }

    public RoadNode node(NodeId id) {
        return nodes.get(id);
    }

    public Map<NodeId, RoadNode> nodes() {
        return nodes;
    }

    public List<RoadEdge> outgoing(NodeId id) {
        return outgoing.getOrDefault(id, List.of());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return outgoing.values().stream().mapToInt(List::size).sum();
    }
}
