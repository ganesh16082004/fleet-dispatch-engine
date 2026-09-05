package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.cache.DriverLocationCache;
import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.events.FleetEventPublisher;
import com.ganesh.fleetdispatch.events.FleetEventType;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class DriverService {
    private final DriverRepository driverRepository;
    private final DriverStateStore driverStateStore;
    private final DriverLocationCache driverLocationCache;
    private final FleetEventPublisher eventPublisher;

    public DriverService(
            DriverRepository driverRepository,
            DriverStateStore driverStateStore,
            DriverLocationCache driverLocationCache,
            FleetEventPublisher eventPublisher) {
        this.driverRepository = driverRepository;
        this.driverStateStore = driverStateStore;
        this.driverLocationCache = driverLocationCache;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DriverDocument create(DriverRequest request) {
        if (request.id() < 0 || request.currentNode() < 0) {
            throw new IllegalArgumentException("id and currentNode must be non-negative");
        }
        DriverStatus status = parseStatus(request.status());
        NodeId node = new NodeId(request.currentNode());

        Driver existing = driverStateStore.getDriver(request.id()).orElse(null);
        boolean reconnecting = existing != null;
        if (!reconnecting) {
            driverStateStore.addDriver(new Driver(request.id(), node, status));
        } else {
            driverStateStore.updateLocation(request.id(), node);
            driverStateStore.updateStatus(request.id(), status);
        }

        driverLocationCache.put(request.id(), node);
        DriverDocument saved = driverRepository.save(
                new DriverDocument(request.id(), request.currentNode(), status.name()));

        String aggregateId = "driver-" + request.id();
        if (reconnecting) {
            eventPublisher.publish(
                    FleetEventType.DRIVER_RECONNECTED,
                    aggregateId,
                    "DRIVER",
                    Map.of("driverId", request.id(), "currentNode", request.currentNode(), "status", status.name()));
        } else {
            eventPublisher.publish(
                    FleetEventType.DRIVER_REGISTERED,
                    aggregateId,
                    "DRIVER",
                    Map.of("driverId", request.id(), "currentNode", request.currentNode(), "status", status.name()));
        }

        eventPublisher.publish(
                FleetEventType.DRIVER_LOCATION_UPDATED,
                aggregateId,
                "DRIVER",
                Map.of("driverId", request.id(), "currentNode", request.currentNode()));

        return saved;
    }

    public List<DriverDocument> findAll() {
        return driverRepository.findAll().stream()
                .sorted(Comparator.comparingLong(DriverDocument::id).reversed())
                .toList();
    }

    public DriverDocument findById(long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException(id));
    }

    public Map<String, Object> getLiveLocation(long id) {
        return driverLocationCache.get(id)
                .<Map<String, Object>>map(node -> Map.of(
                        "driverId", id,
                        "currentNode", node.value(),
                        "source", "redis",
                        "live", true))
                .orElseGet(() -> Map.of(
                        "driverId", id,
                        "source", "redis",
                        "live", false));
    }

    public void delete(long id) {
        if (!driverRepository.existsById(id)) {
            throw new DriverNotFoundException(id);
        }
        driverRepository.deleteById(id);
        driverLocationCache.remove(id);
    }

    private static DriverStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        try {
            return DriverStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown driver status: " + value, exception);
        }
    }
}
