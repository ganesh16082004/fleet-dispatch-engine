package com.ganesh.fleetdispatch.dispatch;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only operator stream for live fleet snapshots and location events. */
public final class DashboardWebSocketHandler implements WebSocketHandler, DriverLocationListener {
    private final DriverStateStore driverStateStore;
    private final DriverHeartbeatStore heartbeatStore;
    private final ConcurrentHashMap<String, WebSocketSession> clients = new ConcurrentHashMap<>();

    public DashboardWebSocketHandler(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore heartbeatStore) {
        this.driverStateStore = driverStateStore;
        this.heartbeatStore = heartbeatStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        clients.put(session.getId(), session);
        send(session, snapshot());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // The dashboard is intentionally read-only for this phase.
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // Connection cleanup is centralized in afterConnectionClosed.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        clients.remove(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    public void onLocationUpdate(DriverLocationUpdate update) {
        String event = locationEvent(update);
        for (WebSocketSession client : clients.values()) {
            try {
                send(client, event);
            } catch (IOException ignored) {
                // The WebSocket lifecycle will remove failed connections.
            }
        }
    }

    public void broadcastSnapshot() {
        String snapshot = snapshot();
        for (WebSocketSession client : clients.values()) {
            try {
                send(client, snapshot);
            } catch (IOException ignored) {
                // The WebSocket lifecycle will remove failed connections.
            }
        }
    }

    private static void send(WebSocketSession client, String payload) throws IOException {
        if (client.isOpen()) {
            client.sendMessage(new TextMessage(payload));
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
