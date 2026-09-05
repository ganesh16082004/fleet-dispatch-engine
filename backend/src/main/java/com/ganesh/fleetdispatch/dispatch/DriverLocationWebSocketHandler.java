package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Spring WebSocket endpoint for ordered driver telemetry on the same HTTP port as the API. */
public final class DriverLocationWebSocketHandler implements WebSocketHandler {
    private final DriverLocationTracker locationTracker;
    private final DriverLocationMessageCodec messageCodec;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public DriverLocationWebSocketHandler(
            DriverLocationTracker locationTracker,
            DriverLocationMessageCodec messageCodec) {
        this.locationTracker = locationTracker;
        this.messageCodec = messageCodec;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        long driverId;
        try {
            driverId = parseDriverId(session.getUri());
        } catch (RuntimeException exception) {
            session.close(new CloseStatus(1008, exception.getMessage()));
            return;
        }

        UUID sessionId = UUID.randomUUID();
        locationTracker.registerSession(driverId, sessionId, System.currentTimeMillis());
        connections.put(session.getId(), new Connection(driverId, sessionId));
        session.sendMessage(new TextMessage("{\"type\":\"session\",\"accepted\":true}"));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (!(message instanceof TextMessage textMessage)) {
            sendAck(session, false, "INVALID_MESSAGE");
            return;
        }

        Connection connection = connections.get(session.getId());
        if (connection == null) {
            session.close(new CloseStatus(1008, "Unknown connection"));
            return;
        }

        try {
            DriverLocationPayload payload = messageCodec.decode(textMessage.getPayload());
            DriverLocationUpdate update = new DriverLocationUpdate(
                    connection.driverId(),
                    connection.sessionId(),
                    payload.sequenceNumber(),
                    new NodeId(payload.nodeId()),
                    payload.timestampMillis());

            boolean accepted = locationTracker.update(update);
            if (accepted) {
                sendAck(session, true, null);
            } else {
                sendAck(session, false, "STALE_OR_WRONG_SESSION");
            }
        } catch (RuntimeException exception) {
            sendAck(session, false, "INVALID_MESSAGE");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // Connection cleanup is centralized in afterConnectionClosed.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        Connection connection = connections.remove(session.getId());
        if (connection != null) {
            locationTracker.closeSession(connection.driverId(), connection.sessionId());
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private static void sendAck(WebSocketSession session, boolean accepted, String reason) throws IOException {
        String payload = accepted
                ? "{\"type\":\"location_ack\",\"accepted\":true}"
                : "{\"type\":\"location_ack\",\"accepted\":false,\"reason\":\"" + reason + "\"}";
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(payload));
        }
    }

    private static long parseDriverId(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Expected WebSocket path /ws/drivers/{driverId}");
        }

        String path = uri.getPath();
        String prefix = "/ws/drivers/";
        if (path == null || !path.startsWith(prefix)) {
            throw new IllegalArgumentException("Expected WebSocket path /ws/drivers/{driverId}");
        }

        String suffix = path.substring(prefix.length());
        if (suffix.isBlank() || suffix.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Expected WebSocket path /ws/drivers/{driverId}");
        }

        try {
            long driverId = Long.parseLong(suffix);
            if (driverId < 0) {
                throw new IllegalArgumentException("driverId must be non-negative");
            }
            return driverId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid driverId: " + suffix, exception);
        }
    }

    private record Connection(long driverId, UUID sessionId) {
    }
}
