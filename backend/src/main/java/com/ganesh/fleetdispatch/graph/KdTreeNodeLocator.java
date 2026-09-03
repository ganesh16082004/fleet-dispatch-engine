package com.ganesh.fleetdispatch.graph;

import com.ganesh.fleetdispatch.domain.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Exact nearest-node lookup backed by a balanced three-dimensional KD-tree.
 *
 * <p>Nodes are represented as unit vectors on the Earth sphere. Euclidean
 * chord distance is monotonic with great-circle distance, so nearest-neighbor
 * ordering is identical to Haversine distance while allowing safe KD-tree
 * branch-and-bound pruning.</p>
 */
public final class KdTreeNodeLocator implements NodeLocator {
    private final KdNode root;

    public KdTreeNodeLocator(RoadGraph graph) {
        Objects.requireNonNull(graph, "graph");
        List<RoadNode> nodes = new ArrayList<>(graph.nodes().values());
        this.root = build(nodes, 0);
        if (root == null) {
            throw new IllegalArgumentException("Cannot build a node locator from an empty road graph");
        }
    }

    @Override
    public NodeId findNearest(Location location) {
        Objects.requireNonNull(location, "location");
        SearchResult result = search(root, toUnitVector(location), null, Double.POSITIVE_INFINITY);
        return result.node.id();
    }

    private static KdNode build(List<RoadNode> nodes, int depth) {
        if (nodes.isEmpty()) {
            return null;
        }

        int axis = depth % 3;
        nodes.sort(Comparator.comparingDouble(node -> coordinate(toUnitVector(node.location()), axis)));
        int median = nodes.size() / 2;
        RoadNode point = nodes.get(median);

        List<RoadNode> left = new ArrayList<>(nodes.subList(0, median));
        List<RoadNode> right = new ArrayList<>(nodes.subList(median + 1, nodes.size()));

        return new KdNode(
                point,
                toUnitVector(point.location()),
                axis,
                build(left, depth + 1),
                build(right, depth + 1));
    }

    private static SearchResult search(
            KdNode node,
            Vector3 target,
            RoadNode bestNode,
            double bestDistanceSquared) {
        if (node == null) {
            return new SearchResult(bestNode, bestDistanceSquared);
        }

        double distanceSquared = squaredDistance(target, node.pointVector);
        if (distanceSquared < bestDistanceSquared) {
            bestNode = node.point;
            bestDistanceSquared = distanceSquared;
        }

        double delta = coordinate(target, node.axis) - coordinate(node.pointVector, node.axis);
        KdNode near = delta <= 0 ? node.left : node.right;
        KdNode far = delta <= 0 ? node.right : node.left;

        SearchResult result = search(near, target, bestNode, bestDistanceSquared);
        bestNode = result.node;
        bestDistanceSquared = result.distanceSquared;

        // Distance to the splitting plane is a valid lower bound in 3D
        // Euclidean space, so pruning cannot discard the true nearest node.
        if (far != null && delta * delta <= bestDistanceSquared) {
            result = search(far, target, bestNode, bestDistanceSquared);
        }

        return result;
    }

    private static Vector3 toUnitVector(Location location) {
        double latitude = Math.toRadians(location.latitude());
        double longitude = Math.toRadians(location.longitude());
        double cosLatitude = Math.cos(latitude);
        return new Vector3(
                cosLatitude * Math.cos(longitude),
                cosLatitude * Math.sin(longitude),
                Math.sin(latitude));
    }

    private static double coordinate(Vector3 vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            case 2 -> vector.z;
            default -> throw new IllegalArgumentException("Invalid KD-tree axis: " + axis);
        };
    }

    private static double squaredDistance(Vector3 a, Vector3 b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record Vector3(double x, double y, double z) {
    }

    private record KdNode(
            RoadNode point,
            Vector3 pointVector,
            int axis,
            KdNode left,
            KdNode right) {
    }

    private record SearchResult(RoadNode node, double distanceSquared) {
    }
}
