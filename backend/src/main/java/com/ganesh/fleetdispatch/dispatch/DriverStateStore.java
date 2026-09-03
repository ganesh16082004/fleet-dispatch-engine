package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
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

    /**
     * Atomically changes an AVAILABLE driver at the expected node to BUSY.
     * Returns false if the driver's state changed before reservation.
     */
    boolean reserveDriver(long driverId, NodeId expectedNode);

    /**
     * Atomically releases a BUSY driver back to AVAILABLE if it is still at the
     * expected node. Used to compensate a dispatch reservation that could not
     * be committed on the order side.
     */
    boolean releaseDriver(long driverId, NodeId expectedNode);

    List<Driver> getAvailableDrivers();

    /**
     * Returns available drivers within the supplied geographic radius,
     * capped at maxCandidates. Implementations may use a spatial index.
     */
    List<Driver> getAvailableDriversNear(Location location, double radiusMeters, int maxCandidates);

    int size();
}
