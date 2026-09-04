package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedEventRepository extends MongoRepository<ProcessedEventDocument, String> {
}
