package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDriverStateStore implements DriverStateStore {
    private final ConcurrentHashMap<Long, Driver> drivers = new ConcurrentHashMap<>();

    @Override
    public void addDriver(Driver driver) {
        if (driver == null) {
            throw new NullPointerException("driver must not be null");
        }

        Driver previous = drivers.putIfAbsent(driver.id(), driver);
        if (previous != null) {
            throw new IllegalArgumentException("Driver already exists: " + driver.id());
        }
    }

    @Override
    public Optional<Driver> getDriver(long driverId) {
        return Optional.ofNullable(drivers.get(driverId));
    }

    @Override
    public void updateLocation(long driverId, NodeId newNode) {
        if (newNode == null) {
            throw new NullPointerException("newNode must not be null");
        }

        update(driverId, current -> new Driver(driverId, newNode, current.status()));
    }

    @Override
    public void updateStatus(long driverId, DriverStatus newStatus) {
        if (newStatus == null) {
            throw new NullPointerException("newStatus must not be null");
        }

        update(driverId, current -> new Driver(driverId, current.currentNode(), newStatus));
    }

    @Override
    public boolean reserveDriver(long driverId) {
        return drivers.computeIfPresent(driverId, (id, current) -> {
            if (current.status() != DriverStatus.AVAILABLE) {
                return current;
            }
            return new Driver(current.id(), current.currentNode(), DriverStatus.BUSY);
        }) != null && getDriver(driverId).orElseThrow().status() == DriverStatus.BUSY;
    }

    @Override
    public List<Driver> getAvailableDrivers() {
        List<Driver> result = new ArrayList<>();
        for (Driver driver : drivers.values()) {
            if (driver.status() == DriverStatus.AVAILABLE) {
                result.add(driver);
            }
        }

        result.sort(Comparator.comparingLong(Driver::id));
        return List.copyOf(result);
    }

    @Override
    public int size() {
        return drivers.size();
    }

    private void update(long driverId, java.util.function.UnaryOperator<Driver> updater) {
        drivers.compute(driverId, (id, current) -> {
            if (current == null) {
                throw new NoSuchElementException("Driver not found: " + driverId);
            }
            return updater.apply(current);
        });
    }
}
