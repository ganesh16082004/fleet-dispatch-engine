package com.ganesh.fleetdispatch.benchmark;

import com.ganesh.fleetdispatch.dispatch.CandidateSelector;
import com.ganesh.fleetdispatch.dispatch.DispatchAssignment;
import com.ganesh.fleetdispatch.dispatch.DispatchEngine;
import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverStateStore;
import com.ganesh.fleetdispatch.dispatch.InMemoryOrderStateStore;
import com.ganesh.fleetdispatch.dispatch.Order;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.dispatch.OrderStatus;
import com.ganesh.fleetdispatch.dispatch.TravelTimeDispatchCandidateScorer;
import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import com.ganesh.fleetdispatch.graph.Route;
import com.ganesh.fleetdispatch.routing.Router;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end batch-dispatch benchmark comparing repeated greedy dispatch with
 * the global minimum-cost batch assignment.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class BatchDispatchJmhBenchmark {

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({"10", "50", "100"})
        public int batchSize;

        private List<Order> orders;
        private DispatchEngine greedyEngine;
        private DispatchEngine batchEngine;

        @Setup(Level.Invocation)
        public void setup() {
            Fixture greedy = fixture(batchSize);
            this.greedyEngine = greedy.engine();
            this.orders = greedy.orders();

            Fixture batch = fixture(batchSize);
            this.batchEngine = batch.engine();
        }
    }

    @Benchmark
    public double greedy(BenchmarkState state) {
        double totalCost = 0.0;
        for (Order order : state.orders) {
            totalCost += state.greedyEngine
                    .dispatch(order)
                    .map(DispatchAssignment::driverToPickupRoute)
                    .map(Route::totalTravelTimeSeconds)
                    .orElse(0.0);
        }
        return totalCost;
    }

    @Benchmark
    public double globalBatch(BenchmarkState state) {
        return state.batchEngine.dispatchBatch(state.orders).stream()
                .mapToDouble(assignment -> assignment.driverToPickupRoute().totalTravelTimeSeconds())
                .sum();
    }

    private static Fixture fixture(int size) {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        InMemoryOrderStateStore orders = new InMemoryOrderStateStore();

        Map<NodeId, RoadNode> nodes = new HashMap<>();
        List<Order> batch = new ArrayList<>(size);

        Location commonLocation = new Location(12.9716, 77.5946);
        for (int i = 0; i < size; i++) {
            NodeId driverNode = new NodeId(20_000L + i);
            NodeId pickupNode = new NodeId(30_000L + i);
            nodes.put(driverNode, new RoadNode(driverNode, commonLocation));
            nodes.put(pickupNode, new RoadNode(pickupNode, commonLocation));
            drivers.addDriver(new Driver(40_000L + i, driverNode, DriverStatus.AVAILABLE));

            Order order = new Order(
                    50_000L + i,
                    pickupNode,
                    pickupNode,
                    i,
                    OrderStatus.CREATED);
            orders.addOrder(order);
            batch.add(order);
        }

        RoadGraph graph = new RoadGraph(nodes, List.of());
        CandidateSelector selector = new CandidateSelector(drivers, graph);
        Router router = syntheticRouter();
        DispatchEngine engine = new DispatchEngine(
                selector,
                drivers,
                orders,
                router,
                new TravelTimeDispatchCandidateScorer(),
                1_000.0,
                size);
        return new Fixture(engine, List.copyOf(batch));
    }

    private static Router syntheticRouter() {
        return (source, target) -> {
            int driverIndex = (int) (source.value() - 20_000L);
            int orderIndex = (int) (target.value() - 30_000L);

            // Greedy is deliberately exposed to contention: early orders prefer
            // the same driver, while the global solver can preserve better matches.
            double distance = Math.abs(driverIndex - orderIndex) + 1.0;
            if (orderIndex > 0 && driverIndex == orderIndex - 1) {
                distance *= 8.0;
            }
            double travelTime = distance;
            return new Route(List.of(source, target), travelTime, distance);
        };
    }

    private record Fixture(DispatchEngine engine, List<Order> orders) {
    }
}
