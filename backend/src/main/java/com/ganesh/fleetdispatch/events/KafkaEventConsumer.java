package com.ganesh.fleetdispatch.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    @KafkaListener(
            topics = "${fleet.kafka.topic:fleet.events}",
            groupId = "${KAFKA_CONSUMER_GROUP:fleet-dispatch-engine}")
    public void consume(String event) {
        log.info("Received fleet event: {}", event);
    }
}
