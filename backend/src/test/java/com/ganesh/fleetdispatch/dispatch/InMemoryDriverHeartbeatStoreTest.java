package com.ganesh.fleetdispatch.dispatch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDriverHeartbeatStoreTest {
    @Test
    void shouldKeepLatestSequenceAndHeartbeat() {
        InMemoryDriverHeartbeatStore store = new InMemoryDriverHeartbeatStore();

        assertTrue(store.recordHeartbeat(10L, 1L, 1_000L));
        assertTrue(store.recordHeartbeat(10L, 2L, 2_000L));

        assertEquals(2L, store.getLastSequenceNumber(10L).orElseThrow());
        assertEquals(2_000L, store.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldRejectOlderSequenceEvenWhenTimestampIsNewer() {
        InMemoryDriverHeartbeatStore store = new InMemoryDriverHeartbeatStore();

        assertTrue(store.recordHeartbeat(10L, 2L, 2_000L));
        assertFalse(store.recordHeartbeat(10L, 1L, 3_000L));

        assertEquals(2L, store.getLastSequenceNumber(10L).orElseThrow());
        assertEquals(2_000L, store.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldRejectDuplicateSequenceNumber() {
        InMemoryDriverHeartbeatStore store = new InMemoryDriverHeartbeatStore();

        assertTrue(store.recordHeartbeat(10L, 5L, 2_000L));
        assertFalse(store.recordHeartbeat(10L, 5L, 3_000L));

        assertEquals(5L, store.getLastSequenceNumber(10L).orElseThrow());
        assertEquals(2_000L, store.getLastHeartbeatMillis(10L).orElseThrow());
    }

    @Test
    void shouldReturnTrackedDriversSortedById() {
        InMemoryDriverHeartbeatStore store = new InMemoryDriverHeartbeatStore();
        store.recordHeartbeat(30L, 1L, 1L);
        store.recordHeartbeat(10L, 1L, 1L);
        store.recordHeartbeat(20L, 1L, 1L);

        assertEquals(List.of(10L, 20L, 30L), store.getTrackedDriverIds());
    }
}
