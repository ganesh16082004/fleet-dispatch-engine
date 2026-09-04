package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** WebSocket gateway that translates driver messages into ordered location updates. */
public final class DriverLocationWebSocketServer extends WebSocketServer {
    private final DriverLocationTracker locationTracker;
    private final DriverLocationMessageCodec messageCodec;
    private final ConcurrentHashMap<WebSocket, Connection> connections = new ConcurrentHashMap<>();

    public DriverLocationWebSocketServer(
            InetSocketAddress address,
            DriverLocationTracker locationTracker) {
        this(address, locationTracker, new DriverLocationMessageCodec());
    }

    public DriverLocationWebSocketServer(
            InetSocketAddress address,
            DriverLocationTracker locationTracker,
            DriverLocationMessageCodec messageCodec) {
        super(Objects.requireNonNull(address, "address"));
        this.locationTracker = Objects.requireNonNull(locationTracker, "locationTracker");
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        try {
            long driverId = parseDriverId(handshake.getResourceDescriptor());
            UUID sessionId = UUID.randomUUID();
            locationTracker.registerSession(driverId, sessionId, System.currentTimeMillis());
            connections.put(conn, new Connection(driverId, sessionId));
            conn.send("{\"type\":\"session\",\"accepted\":true}");
        } catch (RuntimeException e) {
            conn.close(CloseFrame.POLICY_VALIDATION, e.getMessage());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Connection connection = connections.get(conn);
        if (connection == null) {
            conn.close(CloseFrame.POLICY_VALIDATION, "Unknown connection");
            return;
        }

        try {
            DriverLocationPayload payload = messageCodec.decode(message);
            DriverLocationUpdate update = new DriverLocationUpdate(
                    connection.driverId(),
                    connection.sessionId(),
                    payload.sequenceNumber(),
                    new NodeId(payload.nodeId()),
                    payload.timestampMillis());

            boolean accepted = locationTracker.update(update);
            conn.send(accepted
                    ? "{\"type\":\"location_ack\",\"accepted\":true}"
                    : "{\"type\":\"location_ack\",\"accepted\":false,\"reason\":\"STALE_OR_WRONG_SESSION\"}");
        } catch (RuntimeException e) {
            conn.send("{\"type\":\"location_ack\",\"accepted\":false,\"reason\":\"INVALID_MESSAGE\"}");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Connection connection = connections.remove(conn);
        if (connection != null) {
            locationTracker.closeSession(connection.driverId(), connection.sessionId());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Network/protocol errors are surfaced through the server lifecycle.
    }

    @Override
    public void onStart() {
        // Hook for logging/metrics when this gateway is embedded in the application.
    }

    private static long parseDriverId(String resourceDescriptor) {
        if (resourceDescriptor == null || !resourceDescriptor.startsWith("/drivers/")) {
            throw new IllegalArgumentException("Expected WebSocket path /drivers/{driverId}");
        }

        String suffix = resourceDescriptor.substring("/drivers/".length());
        int queryStart = suffix.indexOf('?');
        if (queryStart >= 0) {
            suffix = suffix.substring(0, queryStart);
        }
        if (suffix.isBlank() || suffix.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Expected WebSocket path /drivers/{driverId}");
        }

        try {
            long driverId = Long.parseLong(suffix);
            if (driverId < 0) {
                throw new IllegalArgumentException("driverId must be non-negative");
            }
            return driverId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid driverId: " + suffix, e);
        }
    }

    private record Connection(long driverId, UUID sessionId) {
    }
}
