package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;
import com.ganesh.fleetdispatch.routing.Route;
import com.ganesh.fleetdispatch.routing.Router;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Single-command local demonstration: one operator dashboard, live driver streams,
 * and automatic heartbeat-based failure detection.
 */
public final class FleetDashboardDemo {
    private static final int LOCATION_PORT = 8_087;
    private static final int DASHBOARD_PORT = 8_088;
    private static final int HTTP_PORT = 8_090;
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 4_000L;
    private static final List<Long> DRIVER_IDS = List.of(1L, 2L, 3L, 4L, 5L, 6L);

    private FleetDashboardDemo() {
    }

    public static void main(String[] args) throws Exception {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        for (long driverId : DRIVER_IDS) {
            drivers.addDriver(new Driver(driverId, new NodeId(100L + driverId), DriverStatus.AVAILABLE));
        }

        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);

        DriverLocationWebSocketServer locationServer = new DriverLocationWebSocketServer(
                new InetSocketAddress("127.0.0.1", LOCATION_PORT), tracker);
        DashboardWebSocketServer dashboardServer = new DashboardWebSocketServer(
                new InetSocketAddress("127.0.0.1", DASHBOARD_PORT), drivers, heartbeats);
        tracker.addListener(dashboardServer);

        locationServer.start();
        dashboardServer.start();
        HttpServer httpServer = startDashboardHttpServer();

        DispatchEngine dispatchEngine = buildDispatchEngine(drivers);
        PickedUpOrderRecoveryService recoveryService = new PickedUpOrderRecoveryService(
                drivers,
                new InMemoryOrderStateStore(),
                new InMemoryDriverRouteStore(),
                new InMemoryDriverRecoveryQueue());
        DriverFailureDetector failureDetector = new DriverFailureDetector(
                drivers,
                heartbeats,
                recoveryService,
                dispatchEngine,
                HEARTBEAT_TIMEOUT_MILLIS);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(
                () -> {
                    List<DriverFailureDetection> detections = failureDetector.detect(System.currentTimeMillis());
                    if (!detections.isEmpty()) {
                        dashboardServer.broadcastSnapshot();
                    }
                },
                1,
                1,
                TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(
                dashboardServer::broadcastSnapshot,
                1,
                1,
                TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            try {
                locationServer.stop(1_000);
                dashboardServer.stop(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            httpServer.stop(0);
        }));

        System.out.println("Fleet dashboard: http://127.0.0.1:" + HTTP_PORT);
        System.out.println("Location WebSocket: ws://127.0.0.1:" + LOCATION_PORT + "/drivers/{driverId}");
        System.out.println("Dashboard WebSocket: ws://127.0.0.1:" + DASHBOARD_PORT + "/dashboard");
        System.out.println("Driver #4 will intentionally stop publishing after 12 seconds to demonstrate failure detection.");
    }

    private static DispatchEngine buildDispatchEngine(InMemoryDriverStateStore drivers) {
        RoadGraph emptyGraph = new RoadGraph(Map.of(), List.of());
        CandidateSelector selector = new CandidateSelector(drivers, emptyGraph);
        Router router = (source, target) -> new Route(List.of(source, target), 1.0, 1.0);
        return new DispatchEngine(selector, drivers, new InMemoryOrderStateStore(), router, 500.0, 10);
    }

    private static HttpServer startDashboardHttpServer() throws IOException {
        Path dashboard = Path.of("dashboard", "index.html");
        byte[] content = Files.readAllBytes(dashboard);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", HTTP_PORT), 0);
        server.createContext("/", exchange -> serve(exchange, content));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    private static void serve(HttpExchange exchange, byte[] content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, content.length);
        try (var output = exchange.getResponseBody()) {
            output.write(content);
        }
    }
}
