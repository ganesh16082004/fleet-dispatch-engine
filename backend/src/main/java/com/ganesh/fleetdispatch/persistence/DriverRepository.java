package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DriverRepository extends MongoRepository<DriverDocument, Long> {
}
