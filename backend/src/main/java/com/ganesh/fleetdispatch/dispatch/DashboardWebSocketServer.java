package com.ganesh.fleetdispatch.dispatch;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Streams live fleet snapshots and accepted location events to the single operator dashboard. */
public final class DashboardWebSocketServer extends WebSocketServer implements DriverLocationListener {
    private final DriverStateStore driverStateStore;
    private final DriverHeartbeatStore heartbeatStore;
    private final ConcurrentHashMap<WebSocket, Boolean> clients = new ConcurrentHashMap<>();

    public DashboardWebSocketServer(
            InetSocketAddress address,
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore) {
        super(Objects.requireNonNull(address, "address"));
        this.driverStateStore = Objects.requireNonNull(driverStateStore, "driverStateStore");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.put(conn, Boolean.TRUE);
        sendSnapshot(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // The dashboard is intentionally read-only for this phase.
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Server lifecycle owns error reporting in the demo harness.
    }

    @Override
    public void onStart() {
        // No-op hook for embedding/metrics.
    }

    @Override
    public void onLocationUpdate(DriverLocationUpdate update) {
        String event = locationEvent(update);
        for (WebSocket client : clients.keySet()) {
            if (client.isOpen()) {
                client.send(event);
            }
        }
    }

    public void broadcastSnapshot() {
        String snapshot = snapshot();
        for (WebSocket client : clients.keySet()) {
            if (client.isOpen()) {
                client.send(snapshot);
            }
        }
    }

    private void sendSnapshot(WebSocket client) {
        if (client.isOpen()) {
            client.send(snapshot());
        }
    }

    private String snapshot() {
        StringBuilder json = new StringBuilder("{\"type\":\"snapshot\",\"drivers\":[");
        boolean first = true;
        for (long driverId : heartbeatStore.getTrackedDriverIds()) {
            Driver driver = driverStateStore.getDriver(driverId).orElse(null);
            if (driver == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(driverJson(driver));
        }
        return json.append("]}").toString();
    }

    private String driverJson(Driver driver) {
        long heartbeat = heartbeatStore.getLastHeartbeatMillis(driver.id()).orElse(-1L);
        long sequence = heartbeatStore.getLastSequenceNumber(driver.id()).orElse(-1L);
        return "{"
                + "\"driverId\":" + driver.id() + ","
                + "\"nodeId\":" + driver.currentNode().value() + ","
                + "\"status\":\"" + driver.status() + "\"," 
                + "\"sequenceNumber\":" + sequence + ","
                + "\"lastHeartbeatMillis\":" + heartbeat
                + "}";
    }

    private static String locationEvent(DriverLocationUpdate update) {
        return "{"
                + "\"type\":\"location\","
                + "\"driverId\":" + update.driverId() + ","
                + "\"nodeId\":" + update.node().value() + ","
                + "\"sequenceNumber\":" + update.sequenceNumber() + ","
                + "\"timestampMillis\":" + update.timestampMillis()
                + "}";
    }
}
