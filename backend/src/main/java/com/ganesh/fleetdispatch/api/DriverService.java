package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.cache.DriverLocationCache;
import com.ganesh.fleetdispatch.dispatch.Driver;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverService {
    private final DriverRepository driverRepository;
    private final DriverStateStore driverStateStore;
    private final DriverLocationCache driverLocationCache;

    public DriverService(
            DriverRepository driverRepository,
            DriverStateStore driverStateStore,
            DriverLocationCache driverLocationCache) {
        this.driverRepository = driverRepository;
        this.driverStateStore = driverStateStore;
        this.driverLocationCache = driverLocationCache;
    }

    public DriverDocument create(DriverRequest request) {
        if (request.id() < 0 || request.currentNode() < 0) {
            throw new IllegalArgumentException("id and currentNode must be non-negative");
        }
        DriverStatus status = parseStatus(request.status());
        NodeId node = new NodeId(request.currentNode());

        Driver existing = driverStateStore.getDriver(request.id()).orElse(null);
        if (existing == null) {
            driverStateStore.addDriver(new Driver(request.id(), node, status));
        } else {
            driverStateStore.updateLocation(request.id(), node);
            driverStateStore.updateStatus(request.id(), status);
        }

        driverLocationCache.put(request.id(), node);
        return driverRepository.save(new DriverDocument(request.id(), request.currentNode(), status.name()));
    }

    public List<DriverDocument> findAll() {
        return driverRepository.findAll();
    }

    public DriverDocument findById(long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException(id));
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
