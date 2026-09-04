package com.ganesh.fleetdispatch.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventOutboxRepository extends MongoRepository<EventOutboxDocument, String> {
    List<EventOutboxDocument> findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now);

    List<EventOutboxDocument> findTop100ByOrderByCreatedAtDesc();

    Optional<EventOutboxDocument> findByEventId(String eventId);
}
