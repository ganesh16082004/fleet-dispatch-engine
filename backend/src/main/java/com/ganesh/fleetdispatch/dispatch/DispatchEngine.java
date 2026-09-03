package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Converts nearby-driver candidates into actual dispatch decisions. */
public final class DispatchEngine {
    private static final double INFEASIBLE_COST = Double.MAX_VALUE / 4.0;

    private final CandidateSelector candidateSelector;
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final Router router;
    private final DispatchCandidateScorer candidateScorer;
    private final double searchRadiusMeters;
    private final int maxCandidates;

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
        this.candidateSelector = Objects.requireNonNull(candidateSelector, "candidateSelector");
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.orderStateStore = Objects.requireNonNull(orderStateStore, "orderStateStore");
        this.router = Objects.requireNonNull(router, "router");
        this.candidateScorer = Objects.requireNonNull(candidateScorer, "candidateScorer");

        if (!Double.isFinite(searchRadiusMeters) || searchRadiusMeters < 0) {
            throw new IllegalArgumentException("searchRadiusMeters must be finite and non-negative");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }

        this.searchRadiusMeters = searchRadiusMeters;
        this.maxCandidates = maxCandidates;
    }

    /** Attempts to assign an order to an available driver. */
    public Optional<DispatchAssignment> dispatch(Order order) {
        Objects.requireNonNull(order, "order");
        if (order.status() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("Only CREATED orders can be dispatched: " + order.id());
        }

        Optional<Order> currentOrder = orderStateStore.getOrder(order.id());
        if (currentOrder.isEmpty() || currentOrder.get().status() != OrderStatus.CREATED) {
            return Optional.empty();
        }

        List<RoutedCandidate> routedCandidates = candidateSelector
                .select(order, searchRadiusMeters, maxCandidates)
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

            if (!driverStateStore.reserveDriver(driverId, expectedNode)) {
                continue;
            }

            if (orderStateStore.tryAssign(order.id(), driverId)) {
                return Optional.of(new DispatchAssignment(
                        order.id(),
                        driverId,
                        candidate.route()));
            }

            driverStateStore.releaseDriver(driverId, expectedNode);
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
        Set<Long> reservedDrivers = new HashSet<>();

        for (int i = 0; i < assignment.length; i++) {
            int driverIndex = assignment[i];
            if (driverIndex < 0 || costs[i][driverIndex] >= INFEASIBLE_COST) {
                continue;
            }

            Order order = dispatchableOrders.get(i);
            Driver driver = drivers.get(driverIndex);
            NodeId expectedNode = driver.currentNode();

            if (!driverStateStore.reserveDriver(driver.id(), expectedNode)) {
                continue;
            }

            RoutedCandidate routed = routedByOrderAndDriver
                    .get(order.id())
                    .get(driver.id());
            if (routed != null && orderStateStore.tryAssign(order.id(), driver.id())) {
                reservedDrivers.add(driver.id());
                result.add(new DispatchAssignment(
                        order.id(),
                        driver.id(),
                        routed.route()));
            } else {
                driverStateStore.releaseDriver(driver.id(), expectedNode);
            }
        }

        result.sort(Comparator.comparingLong(DispatchAssignment::orderId));
        return List.copyOf(result);
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
}
