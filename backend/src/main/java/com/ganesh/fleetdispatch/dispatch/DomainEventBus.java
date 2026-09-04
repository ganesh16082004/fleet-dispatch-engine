package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Small in-process event bus; V4 can replace the transport with Kafka. */
public final class DomainEventBus {
    private final CopyOnWriteArrayList<Consumer<DomainEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<DomainEvent> subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber"));
    }

    public void unsubscribe(Consumer<DomainEvent> subscriber) {
        if (subscriber != null) {
            subscribers.remove(subscriber);
        }
    }

    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<DomainEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public List<Consumer<DomainEvent>> subscribers() {
        return List.copyOf(subscribers);
    }
}
