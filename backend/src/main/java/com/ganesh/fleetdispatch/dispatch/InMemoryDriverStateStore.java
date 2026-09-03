package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.domain.Location;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.RoadGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InMemoryDriverStateStore implements DriverStateStore {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double GRID_CELL_METERS = 250.0;
    private static final double METERS_PER_DEGREE = 111_320.0;
    private static final double CELL_DEGREES = GRID_CELL_METERS / METERS_PER_DEGREE;

    private static final Comparator<DriverDistance> BEST_FIRST = Comparator
            .comparingDouble(DriverDistance::distanceKey)
            .thenComparingLong(item -> item.driver().id());

    private final ConcurrentHashMap<Long, Driver> drivers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GridCell, Set<Long>> availableByCell = new ConcurrentHashMap<>();
    private final RoadGraph roadGraph;
    private final H3AvailableDriverIndex h3Index;

    public InMemoryDriverStateStore() {
        this.roadGraph = null;
        this.h3Index = null;
    }

    public InMemoryDriverStateStore(RoadGraph roadGraph) {
        this(roadGraph, SpatialIndexType.GRID);
    }

    public InMemoryDriverStateStore(RoadGraph roadGraph, SpatialIndexType spatialIndexType) {
        this.roadGraph = Objects.requireNonNull(roadGraph, "roadGraph");
        Objects.requireNonNull(spatialIndexType, "spatialIndexType");
        this.h3Index = spatialIndexType == SpatialIndexType.H3
                ? new H3AvailableDriverIndex(roadGraph)
                : null;
    }

    @Override
    public void addDriver(Driver driver) {
        Objects.requireNonNull(driver, "driver must not be null");
        Driver previous = drivers.putIfAbsent(driver.id(), driver);
        if (previous != null) {
            throw new IllegalArgumentException("Driver already exists: " + driver.id());
        }
        if (driver.status() == DriverStatus.AVAILABLE) {
            indexAvailableDriver(driver);
        }
    }

    @Override
    public Optional<Driver> getDriver(long driverId) {
        return Optional.ofNullable(drivers.get(driverId));
    }

    @Override
    public void updateLocation(long driverId, NodeId newNode) {
        Objects.requireNonNull(newNode, "newNode must not be null");
        update(driverId, current -> new Driver(driverId, newNode, current.status()));
    }

    @Override
    public void updateStatus(long driverId, DriverStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        update(driverId, current -> new Driver(driverId, current.currentNode(), newStatus));
    }

    @Override
    public boolean reserveDriver(long driverId) {
        AtomicBoolean reserved = new AtomicBoolean(false);
        drivers.computeIfPresent(driverId, (id, current) -> {
            if (current.status() != DriverStatus.AVAILABLE) {
                return current;
            }
            reserved.set(true);
            removeFromIndex(current);
            return new Driver(current.id(), current.currentNode(), DriverStatus.BUSY);
        });
        return reserved.get();
    }

    @Override
    public boolean reserveDriver(long driverId, NodeId expectedNode) {
        Objects.requireNonNull(expectedNode, "expectedNode must not be null");
        AtomicBoolean reserved = new AtomicBoolean(false);
        drivers.computeIfPresent(driverId, (id, current) -> {
            if (current.status() != DriverStatus.AVAILABLE
                    || !current.currentNode().equals(expectedNode)) {
                return current;
            }
            reserved.set(true);
            removeFromIndex(current);
            return new Driver(current.id(), current.currentNode(), DriverStatus.BUSY);
        });
        return reserved.get();
    }

    @Override
    public boolean releaseDriver(long driverId, NodeId expectedNode) {
        Objects.requireNonNull(expectedNode, "expectedNode must not be null");
        AtomicBoolean released = new AtomicBoolean(false);
        drivers.computeIfPresent(driverId, (id, current) -> {
            if (current.status() != DriverStatus.BUSY
                    || !current.currentNode().equals(expectedNode)) {
                return current;
            }
            released.set(true);
            Driver releasedDriver = new Driver(current.id(), current.currentNode(), DriverStatus.AVAILABLE);
            indexAvailableDriver(releasedDriver);
            return releasedDriver;
        });
        return released.get();
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
    public List<Driver> getAvailableDriversNear(
            Location location,
            double radiusMeters,
            int maxCandidates) {
        Objects.requireNonNull(location, "location must not be null");
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0) {
            throw new IllegalArgumentException("radiusMeters must be finite and non-negative");
        }
        if (maxCandidates <= 0) {
            throw new IllegalArgumentException("maxCandidates must be positive");
        }

        if (roadGraph == null) {
            return getAvailableDrivers();
        }

        if (h3Index != null) {
            Comparator<H3AvailableDriverIndex.DriverDistance> ordering = Comparator
                    .comparingDouble(H3AvailableDriverIndex.DriverDistance::distanceKey)
                    .thenComparingLong(item -> item.driver().id());
            return h3Index.query(location, radiusMeters, maxCandidates, drivers::get, ordering);
        }

        double latitudeDelta = metersToLatitudeDegrees(radiusMeters);
        double longitudeDelta = metersToLongitudeDegrees(radiusMeters, location.latitude());
        int minX = cellX(location.longitude() - longitudeDelta);
        int maxX = cellX(location.longitude() + longitudeDelta);
        int minY = cellY(location.latitude() - latitudeDelta);
        int maxY = cellY(location.latitude() + latitudeDelta);

        double maxHaversineH = Math.pow(
                Math.sin(radiusMeters / (2.0 * EARTH_RADIUS_METERS)),
                2.0);

        PriorityQueue<DriverDistance> topK = new PriorityQueue<>(
                Math.max(1, maxCandidates),
                BEST_FIRST.reversed());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Set<Long> ids = availableByCell.get(new GridCell(x, y));
                if (ids == null) {
                    continue;
                }
                for (Long id : ids) {
                    Driver driver = drivers.get(id);
                    if (driver == null || driver.status() != DriverStatus.AVAILABLE) {
                        continue;
                    }
                    Location driverLocation = locationOf(driver.currentNode());
                    if (driverLocation == null) {
                        continue;
                    }
                    double h = haversineH(location, driverLocation);
                    if (h > maxHaversineH) {
                        continue;
                    }

                    DriverDistance candidate = new DriverDistance(driver, h);
                    if (topK.size() < maxCandidates) {
                        topK.offer(candidate);
                    } else if (BEST_FIRST.compare(candidate, topK.peek()) < 0) {
                        topK.poll();
                        topK.offer(candidate);
                    }
                }
            }
        }

        List<DriverDistance> result = new ArrayList<>(topK);
        result.sort(BEST_FIRST);
        return result.stream()
                .map(DriverDistance::driver)
                .toList();
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
            Driver updated = updater.apply(current);
            if (current.status() == DriverStatus.AVAILABLE) {
                removeFromIndex(current);
            }
            if (updated.status() == DriverStatus.AVAILABLE) {
                indexAvailableDriver(updated);
            }
            return updated;
        });
    }

    private void indexAvailableDriver(Driver driver) {
        if (roadGraph == null) {
            return;
        }
        if (h3Index != null) {
            h3Index.add(driver);
            return;
        }
        Location location = locationOf(driver.currentNode());
        if (location == null) {
            return;
        }
        availableByCell
                .computeIfAbsent(cellFor(location), ignored -> ConcurrentHashMap.newKeySet())
                .add(driver.id());
    }

    private void removeFromIndex(Driver driver) {
        if (roadGraph == null) {
            return;
        }
        if (h3Index != null) {
            h3Index.remove(driver);
            return;
        }
        Location location = locationOf(driver.currentNode());
        if (location == null) {
            return;
        }
        GridCell cell = cellFor(location);
        Set<Long> ids = availableByCell.get(cell);
        if (ids != null) {
            ids.remove(driver.id());
            if (ids.isEmpty()) {
                availableByCell.remove(cell, ids);
            }
        }
    }

    private Location locationOf(NodeId nodeId) {
        var node = roadGraph.node(nodeId);
        return node == null ? null : node.location();
    }

    private static GridCell cellFor(Location location) {
        return new GridCell(cellX(location.longitude()), cellY(location.latitude()));
    }

    private static int cellX(double longitude) {
        return (int) Math.floor(longitude / CELL_DEGREES);
    }

    private static int cellY(double latitude) {
        return (int) Math.floor(latitude / CELL_DEGREES);
    }

    private static double metersToLatitudeDegrees(double meters) {
        return meters / METERS_PER_DEGREE;
    }

    private static double metersToLongitudeDegrees(double meters, double latitude) {
        return meters / Math.max(METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude)), 1.0);
    }

    private static double haversineH(Location a, Location b) {
        double phi1 = Math.toRadians(a.latitude());
        double phi2 = Math.toRadians(b.latitude());
        double dPhi = Math.toRadians(b.latitude() - a.latitude());
        double dLambda = Math.toRadians(b.longitude() - a.longitude());
        double sinPhi = Math.sin(dPhi / 2.0);
        double sinLambda = Math.sin(dLambda / 2.0);
        return sinPhi * sinPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
    }

    private record GridCell(int x, int y) {
    }

    private record DriverDistance(Driver driver, double distanceKey) {
    }
}
