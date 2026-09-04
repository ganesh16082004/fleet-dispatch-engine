package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates insertion of one new order into an existing pickup/drop-off plan.
 * The algorithm is intentionally bounded by three active orders, so exhaustive
 * insertion of the new pickup and drop-off remains cheap.
 */
public final class RouteInsertionEngine {
    private final Router router;
    private final double maxExistingEtaIncreaseSeconds;
    private final double maxNewOrderDeliveryEtaSeconds;

    public RouteInsertionEngine(
            Router router,
            double maxExistingEtaIncreaseSeconds,
            double maxNewOrderDeliveryEtaSeconds) {
        this.router = Objects.requireNonNull(router, "router");
        validateNonNegative(maxExistingEtaIncreaseSeconds, "maxExistingEtaIncreaseSeconds");
        validateNonNegative(maxNewOrderDeliveryEtaSeconds, "maxNewOrderDeliveryEtaSeconds");
        this.maxExistingEtaIncreaseSeconds = maxExistingEtaIncreaseSeconds;
        this.maxNewOrderDeliveryEtaSeconds = maxNewOrderDeliveryEtaSeconds;
    }

    public Optional<RouteInsertionResult> evaluate(
            NodeId currentNode,
            DriverRoutePlan currentPlan,
            Order newOrder) {
        return evaluate(
                currentNode,
                currentPlan,
                newOrder,
                new DeliveryConstraints(
                        maxExistingEtaIncreaseSeconds,
                        maxNewOrderDeliveryEtaSeconds));
    }

    public Optional<RouteInsertionResult> evaluate(
            NodeId currentNode,
            DriverRoutePlan currentPlan,
            Order newOrder,
            DeliveryConstraints constraints) {
        Objects.requireNonNull(currentNode, "currentNode");
        Objects.requireNonNull(currentPlan, "currentPlan");
        Objects.requireNonNull(newOrder, "newOrder");
        Objects.requireNonNull(constraints, "constraints");

        if (currentPlan.activeDeliveryCount() >= DriverRoutePlan.MAX_ACTIVE_DELIVERIES) {
            return Optional.empty();
        }

        RouteSnapshot baseline = route(currentNode, currentPlan.stops());
        if (baseline == null) {
            return Optional.empty();
        }

        List<RouteStop> baseStops = currentPlan.stops();
        RouteInsertionResult best = null;

        for (int pickupIndex = 0; pickupIndex <= baseStops.size(); pickupIndex++) {
            List<RouteStop> withPickup = new ArrayList<>(baseStops);
            withPickup.add(pickupIndex,
                    new RouteStop(newOrder.id(), RouteStopType.PICKUP, newOrder.pickupNode()));

            for (int dropoffIndex = pickupIndex + 1; dropoffIndex <= withPickup.size(); dropoffIndex++) {
                List<RouteStop> stops = new ArrayList<>(withPickup);
                stops.add(dropoffIndex,
                        new RouteStop(newOrder.id(), RouteStopType.DROPOFF, newOrder.dropoffNode()));

                RouteSnapshot candidate = route(currentNode, stops);
                if (candidate == null) {
                    continue;
                }

                double maxExistingIncrease = maxExistingEtaIncrease(
                        baseline.dropoffEtaByOrderId(),
                        candidate.dropoffEtaByOrderId());
                if (maxExistingIncrease > constraints.maxExistingDeliveryEtaIncreaseSeconds()) {
                    continue;
                }

                Double newEta = candidate.dropoffEtaByOrderId().get(newOrder.id());
                if (newEta == null || newEta > constraints.maxNewOrderDeliveryEtaSeconds()) {
                    continue;
                }

                DriverRoutePlan plan = new DriverRoutePlan(
                        appendOrder(currentPlan.activeOrders(), newOrder),
                        stops);
                RouteInsertionResult result = new RouteInsertionResult(
                        plan,
                        candidate.route(),
                        Math.max(0.0,
                                candidate.route().totalTravelTimeSeconds()
                                        - baseline.route().totalTravelTimeSeconds()),
                        Math.max(0.0,
                                candidate.route().totalDistanceMeters()
                                        - baseline.route().totalDistanceMeters()),
                        newEta,
                        Math.max(0.0, maxExistingIncrease));

                if (best == null || better(result, best)) {
                    best = result;
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private List<Order> appendOrder(List<Order> orders, Order newOrder) {
        List<Order> result = new ArrayList<>(orders.size() + 1);
        result.addAll(orders);
        result.add(newOrder);
        return result;
    }

    private boolean better(RouteInsertionResult left, RouteInsertionResult right) {
        int byTime = Double.compare(
                left.incrementalTravelTimeSeconds(), right.incrementalTravelTimeSeconds());
        if (byTime != 0) {
            return byTime < 0;
        }
        int byDistance = Double.compare(
                left.incrementalDistanceMeters(), right.incrementalDistanceMeters());
        if (byDistance != 0) {
            return byDistance < 0;
        }
        int byEtaImpact = Double.compare(
                left.maxExistingDeliveryEtaIncreaseSeconds(),
                right.maxExistingDeliveryEtaIncreaseSeconds());
        return byEtaImpact < 0;
    }

    private RouteSnapshot route(NodeId currentNode, List<RouteStop> stops) {
        List<NodeId> nodes = new ArrayList<>();
        nodes.add(currentNode);
        double totalTime = 0.0;
        double totalDistance = 0.0;
        Map<Long, Double> dropoffEta = new HashMap<>();

        NodeId from = currentNode;
        Set<Long> pickedUp = new HashSet<>();
        for (RouteStop stop : stops) {
            Route segment;
            try {
                segment = router.findRoute(from, stop.nodeId());
            } catch (IllegalArgumentException ignored) {
                return null;
            }

            totalTime += segment.totalTravelTimeSeconds();
            totalDistance += segment.totalDistanceMeters();
            List<NodeId> segmentNodes = segment.nodes();
            for (int i = 1; i < segmentNodes.size(); i++) {
                nodes.add(segmentNodes.get(i));
            }
            from = stop.nodeId();

            if (stop.type() == RouteStopType.PICKUP) {
                pickedUp.add(stop.orderId());
            } else {
                if (!pickedUp.contains(stop.orderId())) {
                    return null;
                }
                dropoffEta.put(stop.orderId(), totalTime);
            }
        }

        return new RouteSnapshot(
                new Route(nodes, totalTime, totalDistance),
                Map.copyOf(dropoffEta));
    }

    private double maxExistingEtaIncrease(
            Map<Long, Double> baseline,
            Map<Long, Double> candidate) {
        double max = 0.0;
        for (Map.Entry<Long, Double> entry : baseline.entrySet()) {
            Double candidateEta = candidate.get(entry.getKey());
            if (candidateEta == null) {
                return Double.POSITIVE_INFINITY;
            }
            max = Math.max(max, candidateEta - entry.getValue());
        }
        return max;
    }

    private static void validateNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private record RouteSnapshot(Route route, Map<Long, Double> dropoffEtaByOrderId) {
    }
}
