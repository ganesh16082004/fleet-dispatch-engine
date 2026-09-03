package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateValidOrder() {
        Order order = new Order(
                1L,
                new NodeId(100L),
                new NodeId(200L),
                1_000L,
                OrderStatus.CREATED
        );

        assertEquals(1L, order.id());
        assertEquals(new NodeId(100L), order.pickupNode());
        assertEquals(new NodeId(200L), order.dropoffNode());
        assertEquals(1_000L, order.requestTimestamp());
        assertEquals(OrderStatus.CREATED, order.status());
    }

    @Test
    void shouldRejectNegativeId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                        -1L,
                        new NodeId(100L),
                        new NodeId(200L),
                        1_000L,
                        OrderStatus.CREATED
                )
        );
    }

    @Test
    void shouldRejectNegativeTimestamp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Order(
                        1L,
                        new NodeId(100L),
                        new NodeId(200L),
                        -1L,
                        OrderStatus.CREATED
                )
        );
    }

    @Test
    void shouldRejectNullPickup() {
        assertThrows(
                NullPointerException.class,
                () -> new Order(
                        1L,
                        null,
                        new NodeId(200L),
                        1_000L,
                        OrderStatus.CREATED
                )
        );
    }

    @Test
    void shouldRejectNullDropoff() {
        assertThrows(
                NullPointerException.class,
                () -> new Order(
                        1L,
                        new NodeId(100L),
                        null,
                        1_000L,
                        OrderStatus.CREATED
                )
        );
    }

    @Test
    void shouldRejectNullStatus() {
        assertThrows(
                NullPointerException.class,
                () -> new Order(
                        1L,
                        new NodeId(100L),
                        new NodeId(200L),
                        1_000L,
                        null
                )
        );
    }
}
