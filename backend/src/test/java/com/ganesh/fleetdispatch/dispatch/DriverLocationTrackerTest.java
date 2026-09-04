package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DriverLocationTrackerTest {
    @Test
    void shouldApplyNewerSequenceAndHeartbeat() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);
        UUID session = tracker.registerDriver(10L, 500L);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, session, 1L, new NodeId(2L), 1_000L)));

        assertEquals(new NodeId(2L), drivers.getDriver(10L).orElseThrow().currentNode());
        assertEquals(1L, heartbeats.getLastSequenceNumber(10L).orElseThrow());
        assertEquals(1_000L, heartbeats.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldIgnoreOutOfOrderSequenceEvenWhenTimestampIsNewer() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);
        UUID session = tracker.registerDriver(10L, 500L);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, session, 2L, new NodeId(2L), 2_000L)));
        assertFalse(tracker.update(new DriverLocationUpdate(10L, session, 1L, new NodeId(3L), 3_000L)));

        assertEquals(new NodeId(2L), drivers.getDriver(10L).orElseThrow().currentNode());
        assertEquals(2L, heartbeats.getLastSequenceNumber(10L).orElseThrow());
        assertEquals(2_000L, heartbeats.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldIgnoreDuplicateSequenceNumber() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);
        UUID session = tracker.registerDriver(10L, 500L);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, session, 7L, new NodeId(2L), 2_000L)));
        assertFalse(tracker.update(new DriverLocationUpdate(10L, session, 7L, new NodeId(3L), 3_000L)));

        assertEquals(new NodeId(2L), drivers.getDriver(10L).orElseThrow().currentNode());
        assertEquals(2_000L, heartbeats.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldRejectPacketsFromPreviousSession() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.AVAILABLE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);
        UUID oldSession = tracker.registerDriver(10L, 500L);

        assertTrue(tracker.update(new DriverLocationUpdate(10L, oldSession, 4L, new NodeId(2L), 1_000L)));

        UUID newSession = UUID.randomUUID();
        tracker.registerSession(10L, newSession, 2_000L);

        assertFalse(tracker.update(new DriverLocationUpdate(10L, oldSession, 99L, new NodeId(3L), 9_000L)));
        assertTrue(tracker.update(new DriverLocationUpdate(10L, newSession, 1L, new NodeId(4L), 2_100L)));
        assertEquals(new NodeId(4L), drivers.getDriver(10L).orElseThrow().currentNode());
    }

    @Test
    void shouldNotResurrectOfflineDriverFromLocationPacket() {
        InMemoryDriverStateStore drivers = new InMemoryDriverStateStore();
        drivers.addDriver(new Driver(10L, new NodeId(1L), DriverStatus.OFFLINE));
        InMemoryDriverHeartbeatStore heartbeats = new InMemoryDriverHeartbeatStore();
        DriverLocationTracker tracker = new DriverLocationTracker(drivers, heartbeats);

        assertThrows(
                IllegalStateException.class,
                () -> tracker.registerSession(10L, UUID.randomUUID(), 2_000L));
    }
}
