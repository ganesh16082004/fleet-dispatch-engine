package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverService {
    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    public DriverDocument create(DriverRequest request) {
        if (request.id() < 0 || request.currentNode() < 0) {
            throw new IllegalArgumentException("id and currentNode must be non-negative");
        }

        if (request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }

        return driverRepository.save(
                new DriverDocument(request.id(), request.currentNode(), request.status().trim().toUpperCase()));
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
    }
}
