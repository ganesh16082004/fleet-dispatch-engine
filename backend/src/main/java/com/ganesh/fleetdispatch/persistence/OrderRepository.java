package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<OrderDocument, Long> {
}
