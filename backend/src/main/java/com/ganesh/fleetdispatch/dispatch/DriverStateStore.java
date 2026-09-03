package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;

import java.util.List;
import java.util.Optional;

public interface DriverStateStore {
    void addDriver(Driver driver);

    Optional<Driver> getDriver(long driverId);

    void updateLocation(long driverId, NodeId newNode);

    void updateStatus(long driverId, DriverStatus newStatus);

    /** Atomically changes an AVAILABLE driver to BUSY if it is still available. */
    boolean reserveDriver(long driverId);

    List<Driver> getAvailableDrivers();

    int size();
}
