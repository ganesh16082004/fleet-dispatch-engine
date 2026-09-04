package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventOutboxRepository extends MongoRepository<EventOutboxDocument, String> {
    List<EventOutboxDocument> findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now);

    Optional<EventOutboxDocument> findByEventId(UUID eventId);
}
