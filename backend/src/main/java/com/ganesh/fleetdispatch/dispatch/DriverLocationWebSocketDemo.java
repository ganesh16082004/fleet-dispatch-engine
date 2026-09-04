package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Small executable demo proving live WebSocket location ingestion and packet ordering. */
public final class DriverLocationWebSocketDemo {
    private static final int PORT = 8_087;
    private static final long DRIVER_ID = 1L;

    private DriverLocationWebSocketDemo() {
    }

    public static void main(String[] args) throws Exception {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(DRIVER_ID, new NodeId(100L), DriverStatus.AVAILABLE));

        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);
        DriverLocationWebSocketServer server = new DriverLocationWebSocketServer(
                new InetSocketAddress("127.0.0.1", PORT),
                tracker);

        server.start();
        try {
            TimeUnit.MILLISECONDS.sleep(250);

            WebSocketClient client = new WebSocketClient(
                    URI.create("ws://127.0.0.1:" + PORT + "/drivers/" + DRIVER_ID)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("Connected to location gateway");
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("Gateway: " + message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Client closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Could not connect WebSocket client");
            }

            long now = System.currentTimeMillis();
            client.send("{\"sequenceNumber\":1,\"nodeId\":101,\"timestampMillis\":" + now + "}");
            client.send("{\"sequenceNumber\":3,\"nodeId\":103,\"timestampMillis\":" + (now + 200) + "}");
            client.send("{\"sequenceNumber\":2,\"nodeId\":102,\"timestampMillis\":" + (now + 300) + "}");
            client.send("{\"sequenceNumber\":4,\"nodeId\":104,\"timestampMillis\":" + (now + 400) + "}");

            TimeUnit.MILLISECONDS.sleep(500);

            Driver driver = drivers.getDriver(DRIVER_ID).orElseThrow();
            System.out.println("Final driver node: " + driver.currentNode().value());
            System.out.println("Final accepted sequence: "
                    + heartbeats.getLastSequenceNumber(DRIVER_ID).orElseThrow());

            client.closeBlocking();
        } finally {
            server.stop(1_000);
        }
    }
}
