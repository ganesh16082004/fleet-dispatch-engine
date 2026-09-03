package com.ganesh.fleetdispatch.benchmark;

import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverStateStore;
import com.ganesh.fleetdispatch.dispatch.SpatialIndexType;
import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.graph.RoadNode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class SpatialIndexComparisonJmhBenchmark {
    @Param({"10000", "100000"})
    public int driverCount;

    private static final int MAX_CANDIDATES = 20;
    private static final double RADIUS_METERS = 2_000.0;
    private static final int QUERY_COUNT = 128;

    private InMemoryDriverStateStore grid;
    private InMemoryDriverStateStore h3;
    private Location[] queries;
    private int queryIndex;

    @Setup
    public void setup() {
        RoadGraph graph = buildGraph(driverCount);
        grid = new InMemoryDriverStateStore(graph, SpatialIndexType.GRID);
        h3 = new InMemoryDriverStateStore(graph, SpatialIndexType.H3);

        for (int i = 0; i < driverCount; i++) {
            Driver driver = new Driver(
                    1_000_000L + i,
                    new NodeId(2_000_000L + i),
                    DriverStatus.AVAILABLE);
            grid.addDriver(driver);
            h3.addDriver(driver);
        }

        queries = new Location[QUERY_COUNT];
        for (int i = 0; i < QUERY_COUNT; i++) {
            queries[i] = new Location(
                    12.90 + ((i * 37) % 160) * 0.001,
                    77.50 + ((i * 53) % 160) * 0.001);
        }
        queryIndex = 0;
    }

    @Benchmark
    public void gridLookup(Blackhole blackhole) {
        blackhole.consume(grid.getAvailableDriversNear(
                nextQuery(), RADIUS_METERS, MAX_CANDIDATES));
    }

    @Benchmark
    public void h3Lookup(Blackhole blackhole) {
        blackhole.consume(h3.getAvailableDriversNear(
                nextQuery(), RADIUS_METERS, MAX_CANDIDATES));
    }

    private Location nextQuery() {
        Location query = queries[queryIndex++ & (QUERY_COUNT - 1)];
        return query;
    }

    private RoadGraph buildGraph(int count) {
        Map<NodeId, RoadNode> nodes = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) {
            NodeId nodeId = new NodeId(2_000_000L + i);
            double latitude = 12.90 + (i % 160) * 0.001;
            double longitude = 77.50 + (i / 160) * 0.001;
            nodes.put(nodeId, new RoadNode(nodeId, new Location(latitude, longitude)));
        }
        return new RoadGraph(nodes, java.util.List.of());
    }
}
