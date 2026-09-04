package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Converts nearby-driver candidates into route-efficient dispatch decisions. */
public final class DispatchEngine {
    private static final double INFEASIBLE_COST = Double.MAX_VALUE / 4.0;
    private static final int DEFAULT_MAX_ACTIVE_DELIVERIES = DriverRoutePlan.MAX_ACTIVE_DELIVERIES;
    private static final double DEFAULT_MAX_EXISTING_ETA_INCREASE_SECONDS = 300.0;
    private static final double DEFAULT_MAX_NEW_ORDER_DELIVERY_ETA_SECONDS = 1_800.0;
    private static final double DEFAULT_RADIUS_EXPANSION_FACTOR = 2.0;

    private final CandidateSelector candidateSelector;
    private final RouteCandidateSelector routeCandidateSelector;
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final DriverRouteStore driverRouteStore;
    private final RouteInsertionEngine routeInsertionEngine;
    private final Router router;
    private final DispatchCandidateScorer candidateScorer;
    private final double searchRadiusMeters;
    private final double maxSearchRadiusMeters;
    private final double radiusExpansionFactor;
    private final int maxCandidates;
    private final ConcurrentHashMap<Long, Object> driverDispatchLocks = new ConcurrentHashMap<>();

    public DispatchEngine(
            CandidateSelector candidateSelector,
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            Router router,
            double searchRadiusMeters,
            int maxCandidates) {
        this(
                candidateSelector,
                driverStateStore,
                orderStateStore,
                router,
                new TravelTimeDispatchCandidateScorer(),
                searchRadiusMeters,
                maxCandidates);
    }

    public DispatchEngine(
            CandidateSelector candidateSelector,
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            Router router,
            DispatchCandidateScorer candidateScorer,
            double searchRadiusMeters,
            int maxCandidates) {
        this(
                candidateSelector,
                driverStateStore,
                orderStateStore,
                router,
                candidateScorer,
                searchRadiusMeters,
                maxCandidates,
                searchRadiusMeters * 4.0,
                DEFAULT_RADIUS_EXPANSION_FACTOR,
                new InMemoryDriverRouteStore(),
                new RouteInsertionEngine(
                        router,
                        DEFAULT_MAX_EXISTING_ETA_INCREASE_SECONDS,
                        DEFAULT_MAX_NEW_ORDER_DELIVERY_ETA_SECONDS));
    }

    public DispatchEngine(
            CandidateSelector candidateSelector,
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            Router router,
            DispatchCandidateScorer candidateScorer,
            double searchRadiusMeters,
            int maxCandidates,
            double maxSearchRadiusMeters,
            double radiusExpansionFactor,
            DriverRouteStore driverRouteStore,
            RouteInsertionEngine routeInsertionEngine) {
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.orderStateStore = Objects.requireNonNull(orderStateStore, "orderStateStore");
        this.router = Objects.requireNonNull(router, "router");
        this.candidateScorer = Objects.requireNonNull(candidateScorer, "candidateScorer");
        this.driverRouteStore = Objects.requireNonNull(driverRouteStore, "driverRouteStore");
        this.routeInsertionEngine = Objects.requireNonNull(routeInsertionEngine, "routeInsertionEngine");

        if (!Double.isFinite(searchRadiusMeters) || searchRadiusMeters < 0) {
            throw new IllegalArgumentException("searchRadiusMeters must be finite and non-negative");
        }
        if (!Double.isFinite(maxSearchRadiusMeters) || maxSearchRadiusMeters < searchRadiusMeters) {
            throw new IllegalArgumentException(
                    "maxSearchRadiusMeters must be finite and >= searchRadiusMeters");
        }
        if (!Double.isFinite(radiusExpansionFactor) || radiusExpansionFactor <= 1.0) {
            throw new IllegalArgumentException("radiusExpansionFactor must be finite and greater than 1");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }

        this.searchRadiusMeters = searchRadiusMeters;
        this.maxSearchRadiusMeters = maxSearchRadiusMeters;
        this.radiusExpansionFactor = radiusExpansionFactor;
        this.maxCandidates = maxCandidates;
        this.routeCandidateSelector = new RouteCandidateSelector(
                driverStateStore,
                driverRouteStore,
                candidateSelector.roadGraph());
    }

    /** Attempts route consolidation first, then falls back to a fresh driver. */
    public Optional<DispatchAssignment> dispatch(Order order) {
        Objects.requireNonNull(order, "order");
        if (order.status() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("Only CREATED orders can be dispatched: " + order.id());
        }

        Optional<Order> currentOrder = orderStateStore.getOrder(order.id());
        if (currentOrder.isEmpty() || currentOrder.get().status() != OrderStatus.CREATED) {
            return Optional.empty();
        }

        driverRouteStore.pruneInactive(orderStateStore);

        for (double radius : searchRadii()) {
            Optional<DispatchAssignment> consolidated = dispatchIntoExistingRoute(order, radius);
            if (consolidated.isPresent()) {
                return consolidated;
            }

            Optional<DispatchAssignment> freshDriver = dispatchToAvailableDriver(order, radius);
            if (freshDriver.isPresent()) {
                return freshDriver;
            }
        }

        return Optional.empty();
    }

    private Optional<DispatchAssignment> dispatchIntoExistingRoute(Order order, double radius) {
        List<RouteCandidateSelector.RouteDriverCandidate> candidates = routeCandidateSelector
                .select(order, radius, maxCandidates);

        List<RouteOption> options = new ArrayList<>();
        for (RouteCandidateSelector.RouteDriverCandidate candidate : candidates) {
            routeInsertionEngine
                    .evaluate(candidate.driver().currentNode(), candidate.plan(), order)
                    .ifPresent(result -> options.add(new RouteOption(candidate.driver(), result)));
        }

        options.sort(Comparator
                .comparingDouble((RouteOption option) ->
                        option.insertion().incrementalTravelTimeSeconds())
                .thenComparingDouble(option -> option.insertion().incrementalDistanceMeters())
                .thenComparingDouble(option -> option.insertion().maxExistingDeliveryEtaIncreaseSeconds())
                .thenComparingLong(option -> option.driver().id()));

        for (RouteOption option : options) {
            Driver driver = option.driver();
            synchronized (driverDispatchLocks.computeIfAbsent(driver.id(), ignored -> new Object())) {
                Driver currentDriver = driverStateStore.getDriver(driver.id()).orElse(null);
                DriverRoutePlan currentPlan = driverRouteStore.getPlan(driver.id()).orElse(null);
                if (currentDriver == null || currentPlan == null
                        || currentDriver.status() != DriverStatus.BUSY
                        || currentPlan.activeDeliveryCount() >= DEFAULT_MAX_ACTIVE_DELIVERIES) {
                    continue;
                }

                Optional<RouteInsertionResult> reevaluated = routeInsertionEngine.evaluate(
                        currentDriver.currentNode(), currentPlan, order);
                if (reevaluated.isEmpty()) {
                    continue;
                }

                RouteInsertionResult insertion = reevaluated.get();
                if (!orderStateStore.tryAssign(order.id(), driver.id())) {
                    continue;
                }

                driverRouteStore.putPlan(driver.id(), insertion.plan());
                return Optional.of(new DispatchAssignment(
                        order.id(),
                        driver.id(),
                        insertion.route()));
            }
        }

        return Optional.empty();
    }

    private Optional<DispatchAssignment> dispatchToAvailableDriver(Order order, double radius) {
        List<RoutedCandidate> routedCandidates = candidateSelector
                .select(order, radius, maxCandidates)
                .stream()
                .map(candidate -> routeCandidate(candidate, order))
                .flatMap(Optional::stream)
                .sorted(this::compareRoutedCandidates)
                .toList();

        for (RoutedCandidate candidate : routedCandidates) {
            DriverCandidate driverCandidate = candidate.candidate();
            Driver driver = driverCandidate.driver();
            long driverId = driver.id();
            NodeId expectedNode = driver.currentNode();

            synchronized (driverDispatchLocks.computeIfAbsent(driverId, ignored -> new Object())) {
                if (!driverStateStore.reserveDriver(driverId, expectedNode)) {
                    continue;
                }

                if (orderStateStore.tryAssign(order.id(), driverId)) {
                    driverRouteStore.putPlan(driverId, DriverRoutePlan.single(order));
                    return Optional.of(new DispatchAssignment(
                            order.id(),
                            driverId,
                            candidate.route()));
                }

                driverStateStore.releaseDriver(driverId, expectedNode);
            }
        }

        return Optional.empty();
    }

    /**
     * Assigns a batch of CREATED orders using a minimum-cost one-to-one matching.
     * Existing driver reservation remains the final concurrency guard.
     */
    public List<DispatchAssignment> dispatchBatch(List<Order> orders) {
        Objects.requireNonNull(orders, "orders");
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Order> dispatchableOrders = orders.stream()
                .map(order -> {
                    Objects.requireNonNull(order, "orders must not contain null");
                    if (order.status() != OrderStatus.CREATED) {
                        throw new IllegalArgumentException(
                                "Only CREATED orders can be dispatched: " + order.id());
                    }
                    Optional<Order> current = orderStateStore.getOrder(order.id());
                    return current.isPresent() && current.get().status() == OrderStatus.CREATED
                            ? order
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();

        if (dispatchableOrders.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RoutedCandidate>> candidatesByOrder = new HashMap<>();
        Map<Long, Driver> driversById = new HashMap<>();
        for (Order order : dispatchableOrders) {
            List<RoutedCandidate> routed = candidateSelector
                    .select(order, searchRadiusMeters, maxCandidates)
                    .stream()
                    .map(candidate -> {
                        driversById.put(candidate.driver().id(), candidate.driver());
                        return routeCandidate(candidate, order);
                    })
                    .flatMap(Optional::stream)
                    .toList();
            candidatesByOrder.put(order.id(), routed);
        }

        if (driversById.isEmpty()) {
            return List.of();
        }

        List<Driver> drivers = new ArrayList<>(driversById.values());
        drivers.sort(Comparator.comparingLong(Driver::id));

        double[][] costs = new double[dispatchableOrders.size()][drivers.size()];
        Map<Long, Map<Long, RoutedCandidate>> routedByOrderAndDriver = new HashMap<>();
        for (int i = 0; i < dispatchableOrders.size(); i++) {
            Order order = dispatchableOrders.get(i);
            Map<Long, RoutedCandidate> byDriver = new HashMap<>();
            for (int j = 0; j < drivers.size(); j++) {
                costs[i][j] = INFEASIBLE_COST;
            }
            for (RoutedCandidate routed : candidatesByOrder.get(order.id())) {
                long driverId = routed.candidate().driver().id();
                byDriver.put(driverId, routed);
                int driverIndex = binarySearchDriver(drivers, driverId);
                costs[i][driverIndex] = candidateScorer.score(
                        routed.candidate(), routed.route());
            }
            routedByOrderAndDriver.put(order.id(), byDriver);
        }

        int[] assignment = minimumCostAssignment(costs);
        List<DispatchAssignment> result = new ArrayList<>();

        for (int i = 0; i < assignment.length; i++) {
            int driverIndex = assignment[i];
            if (driverIndex < 0 || costs[i][driverIndex] >= INFEASIBLE_COST) {
                continue;
            }

            Order order = dispatchableOrders.get(i);
            Driver driver = drivers.get(driverIndex);
            NodeId expectedNode = driver.currentNode();

            synchronized (driverDispatchLocks.computeIfAbsent(driver.id(), ignored -> new Object())) {
                if (!driverStateStore.reserveDriver(driver.id(), expectedNode)) {
                    continue;
                }

                RoutedCandidate routed = routedByOrderAndDriver
                        .get(order.id())
                        .get(driver.id());
                if (routed != null && orderStateStore.tryAssign(order.id(), driver.id())) {
                    driverRouteStore.putPlan(driver.id(), DriverRoutePlan.single(order));
                    result.add(new DispatchAssignment(
                            order.id(),
                            driver.id(),
                            routed.route()));
                } else {
                    driverStateStore.releaseDriver(driver.id(), expectedNode);
                }
            }
        }

        result.sort(Comparator.comparingLong(DispatchAssignment::orderId));
        return List.copyOf(result);
    }

    private List<Double> searchRadii() {
        List<Double> radii = new ArrayList<>();
        double current = searchRadiusMeters;
        radii.add(current);
        while (current < maxSearchRadiusMeters) {
            double next = current == 0.0
                    ? maxSearchRadiusMeters
                    : Math.min(maxSearchRadiusMeters, current * radiusExpansionFactor);
            if (next <= current) {
                break;
            }
            radii.add(next);
            current = next;
        }
        return radii;
    }

    private int compareRoutedCandidates(RoutedCandidate left, RoutedCandidate right) {
        return Comparator
                .comparingDouble((RoutedCandidate candidate) ->
                        candidateScorer.score(candidate.candidate(), candidate.route()))
                .thenComparingDouble(candidate -> candidate.route().totalDistanceMeters())
                .thenComparingLong(candidate -> candidate.candidate().driver().id())
                .compare(left, right);
    }

    private int binarySearchDriver(List<Driver> drivers, long driverId) {
        int low = 0;
        int high = drivers.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long current = drivers.get(mid).id();
            if (current < driverId) {
                low = mid + 1;
            } else if (current > driverId) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        throw new IllegalStateException("Driver missing from batch index: " + driverId);
    }

    /** Hungarian algorithm for minimum-cost rectangular assignment. */
    private int[] minimumCostAssignment(double[][] costs) {
        int rowCount = costs.length;
        int columnCount = costs[0].length;
        boolean transposed = rowCount > columnCount;
        double[][] matrix = transposed ? transpose(costs) : costs;
        int rows = matrix.length;
        int cols = matrix[0].length;

        double[] u = new double[rows + 1];
        double[] v = new double[cols + 1];
        int[] p = new int[cols + 1];
        int[] way = new int[cols + 1];

        for (int i = 1; i <= rows; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[cols + 1];
            boolean[] used = new boolean[cols + 1];
            java.util.Arrays.fill(minv, Double.POSITIVE_INFINITY);

            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;
                for (int j = 1; j <= cols; j++) {
                    if (used[j]) {
                        continue;
                    }
                    double current = matrix[i0 - 1][j - 1] - u[i0] - v[j];
                    if (current < minv[j]) {
                        minv[j] = current;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= cols; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);

            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        if (!transposed) {
            int[] assignment = new int[rowCount];
            java.util.Arrays.fill(assignment, -1);
            for (int j = 1; j <= cols; j++) {
                if (p[j] != 0) {
                    assignment[p[j] - 1] = j - 1;
                }
            }
            return assignment;
        }

        int[] assignment = new int[rowCount];
        java.util.Arrays.fill(assignment, -1);
        for (int j = 1; j <= cols; j++) {
            if (p[j] != 0) {
                int originalColumn = p[j] - 1;
                int originalRow = j - 1;
                assignment[originalRow] = originalColumn;
            }
        }
        return assignment;
    }

    private double[][] transpose(double[][] matrix) {
        double[][] result = new double[matrix[0].length][matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                result[j][i] = matrix[i][j];
            }
        }
        return result;
    }

    private Optional<RoutedCandidate> routeCandidate(DriverCandidate candidate, Order order) {
        try {
            Route route = router.findRoute(candidate.driverNode(), order.pickupNode());
            return Optional.of(new RoutedCandidate(candidate, route));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private record RoutedCandidate(DriverCandidate candidate, Route route) {
    }

    private record RouteOption(Driver driver, RouteInsertionResult insertion) {
    }
}
