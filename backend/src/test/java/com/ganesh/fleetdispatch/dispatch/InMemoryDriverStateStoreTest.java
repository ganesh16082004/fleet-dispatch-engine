package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDriverStateStoreTest {

    @Test
    void shouldAddAndRetrieveDriver() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        Driver driver = new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE);

        store.addDriver(driver);

        assertEquals(1, store.size());
        assertEquals(driver, store.getDriver(1L).orElseThrow());
    }

    @Test
    void shouldRejectDuplicateDriverId() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE));

        assertThrows(
                IllegalArgumentException.class,
                () -> store.addDriver(new Driver(1L, new NodeId(200L), DriverStatus.BUSY))
        );
    }

    @Test
    void shouldUpdateDriverLocation() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE));

        store.updateLocation(1L, new NodeId(250L));

        Driver updated = store.getDriver(1L).orElseThrow();
        assertEquals(new NodeId(250L), updated.currentNode());
        assertEquals(DriverStatus.AVAILABLE, updated.status());
    }

    @Test
    void shouldUpdateDriverStatus() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE));

        store.updateStatus(1L, DriverStatus.BUSY);

        Driver updated = store.getDriver(1L).orElseThrow();
        assertEquals(DriverStatus.BUSY, updated.status());
        assertEquals(new NodeId(100L), updated.currentNode());
    }

    @Test
    void shouldReturnOnlyAvailableDriversSortedById() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(30L, new NodeId(300L), DriverStatus.AVAILABLE));
        store.addDriver(new Driver(10L, new NodeId(100L), DriverStatus.BUSY));
        store.addDriver(new Driver(20L, new NodeId(200L), DriverStatus.AVAILABLE));
        store.addDriver(new Driver(40L, new NodeId(400L), DriverStatus.OFFLINE));

        List<Driver> available = store.getAvailableDrivers();

        assertEquals(List.of(
                new Driver(20L, new NodeId(200L), DriverStatus.AVAILABLE),
                new Driver(30L, new NodeId(300L), DriverStatus.AVAILABLE)
        ), available);
    }

    @Test
    void shouldReturnEmptyForUnknownDriver() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();

        assertTrue(store.getDriver(99L).isEmpty());
    }

    @Test
    void shouldRejectLocationUpdateForUnknownDriver() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();

        assertThrows(
                NoSuchElementException.class,
                () -> store.updateLocation(99L, new NodeId(100L))
        );
    }

    @Test
    void shouldRejectStatusUpdateForUnknownDriver() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();

        assertThrows(
                NoSuchElementException.class,
                () -> store.updateStatus(99L, DriverStatus.AVAILABLE)
        );
    }

    @Test
    void shouldRejectNullDriver() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();

        assertThrows(NullPointerException.class, () -> store.addDriver(null));
    }

    @Test
    void shouldRejectNullLocation() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE));

        assertThrows(
                NullPointerException.class,
                () -> store.updateLocation(1L, null)
        );
    }

    @Test
    void shouldRejectNullStatus() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE));

        assertThrows(
                NullPointerException.class,
                () -> store.updateStatus(1L, null)
        );
    }

    @Test
    void shouldReturnSnapshotThatCannotBeModified() {
        InMemoryDriverStateStore store = new InMemoryDriverStateStore();
        store.addDriver(new Driver(1L, new NodeId(100L), DriverStatus.AVAILABLE));

        List<Driver> available = store.getAvailableDrivers();

        assertThrows(
                UnsupportedOperationException.class,
                () -> available.clear()
        );
        assertEquals(1, store.size());
    }
}
