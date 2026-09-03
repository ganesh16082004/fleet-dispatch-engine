package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOrderStateStoreTest {

    private Order order() {
        return new Order(
                100L,
                new NodeId(10L),
                new NodeId(20L),
                1_000L,
                OrderStatus.CREATED
        );
    }

    @Test
    void shouldAddAndRetrieveOrder() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertEquals(1, store.size());
        assertEquals(order(), store.getOrder(100L).orElseThrow());
        assertTrue(store.getAssignedDriverId(100L).isEmpty());
    }

    @Test
    void shouldRejectDuplicateOrderId() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertThrows(IllegalArgumentException.class, () -> store.addOrder(order()));
    }

    @Test
    void shouldAssignCreatedOrderAtomically() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertTrue(store.tryAssign(100L, 42L));

        assertEquals(OrderStatus.ASSIGNED, store.getOrder(100L).orElseThrow().status());
        assertEquals(42L, store.getAssignedDriverId(100L).orElseThrow());
    }

    @Test
    void shouldNotAssignOrderTwice() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertTrue(store.tryAssign(100L, 42L));
        assertFalse(store.tryAssign(100L, 99L));

        assertEquals(42L, store.getAssignedDriverId(100L).orElseThrow());
    }

    @Test
    void shouldAllowExplicitStateTransition() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertTrue(store.tryTransition(100L, OrderStatus.CREATED, OrderStatus.CANCELLED));
        assertEquals(OrderStatus.CANCELLED, store.getOrder(100L).orElseThrow().status());
        assertFalse(store.tryTransition(100L, OrderStatus.CREATED, OrderStatus.ASSIGNED));
    }

    @Test
    void shouldPreserveAssignedDriverAcrossTransitions() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertTrue(store.tryAssign(100L, 42L));
        assertTrue(store.tryTransition(100L, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP));

        assertEquals(OrderStatus.PICKED_UP, store.getOrder(100L).orElseThrow().status());
        assertEquals(42L, store.getAssignedDriverId(100L).orElseThrow());
    }

    @Test
    void shouldReturnEmptyForUnknownOrder() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();

        assertTrue(store.getOrder(999L).isEmpty());
        assertTrue(store.getAssignedDriverId(999L).isEmpty());
        assertFalse(store.tryAssign(999L, 42L));
    }

    @Test
    void shouldRejectInvalidDriverId() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        assertThrows(IllegalArgumentException.class, () -> store.tryAssign(100L, -1L));
    }

    @Test
    void shouldRejectNullOrder() {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();

        assertThrows(NullPointerException.class, () -> store.addOrder(null));
    }

    @Test
    void shouldAllowExactlyOneConcurrentAssignment() throws Exception {
        InMemoryOrderStateStore store = new InMemoryOrderStateStore();
        store.addOrder(order());

        int workers = 64;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> results = new ArrayList<>();
        Object resultsLock = new Object();

        for (int i = 0; i < workers; i++) {
            final long driverId = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    boolean result = store.tryAssign(100L, driverId);
                    synchronized (resultsLock) {
                        results.add(result);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Worker interrupted");
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertTrue(store.getAssignedDriverId(100L).isPresent());
        assertEquals(OrderStatus.ASSIGNED, store.getOrder(100L).orElseThrow().status());
    }
}
