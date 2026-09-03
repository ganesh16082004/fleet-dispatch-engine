package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverTest {

    @Test
    void shouldCreateValidDriver() {
        Driver driver = new Driver(
                1L,
                new NodeId(100L),
                DriverStatus.AVAILABLE
        );

        assertEquals(1L, driver.id());
        assertEquals(new NodeId(100L), driver.currentNode());
        assertEquals(DriverStatus.AVAILABLE, driver.status());
    }

    @Test
    void shouldRejectNegativeId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Driver(
                        -1L,
                        new NodeId(100L),
                        DriverStatus.AVAILABLE
                )
        );
    }

    @Test
    void shouldRejectNullNode() {
        assertThrows(
                NullPointerException.class,
                () -> new Driver(
                        1L,
                        null,
                        DriverStatus.AVAILABLE
                )
        );
    }

    @Test
    void shouldRejectNullStatus() {
        assertThrows(
                NullPointerException.class,
                () -> new Driver(
                        1L,
                        new NodeId(100L),
                        null
                )
        );
    }
}
