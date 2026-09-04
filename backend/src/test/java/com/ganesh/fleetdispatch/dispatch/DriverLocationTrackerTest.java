package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverLocationTrackerTest {
    @Test
    void shouldApplyNewerLocationAndHeartbeat() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, new NodeId(2L), 1_000L)));

        assertEquals(new NodeId(2L), drivers.getDriver(10L).orElseThrow().currentNode());
        assertEquals(1_000L, heartbeats.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldIgnoreOutOfOrderLocationUpdate() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, new NodeId(2L), 2_000L)));
        assertFalse(tracker.update(new DriverLocationUpdate(10L, new NodeId(3L), 1_500L)));

        assertEquals(new NodeId(2L), drivers.getDriver(10L).orElseThrow().currentNode());
        assertEquals(2_000L, heartbeats.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldNotResurrectOfflineDriverFromLocationPacket() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.OFFLINE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, new NodeId(2L), 2_000L)));

        assertEquals(new NodeId(2L), drivers.getDriver(10L).orElseThrow().currentNode());
        assertEquals(DriverStatus.OFFLINE, drivers.getDriver(10L).orElseThrow().status());
    }
}
