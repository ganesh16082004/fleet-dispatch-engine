package com.ganesh.fleetdispatch.dispatch;

import java.util.Objects;
import java.util.function.Consumer;

/** Exactly-once effect guard for a single process; persistence moves to V4. */
public final class IdempotentEventProcessor {
    private final IdempotencyStore store;

    public IdempotentEventProcessor(IdempotencyStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public boolean process(String eventId, DomainEvent event, Consumer<DomainEvent> handler) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(handler, "handler");
        if (!store.tryStart(eventId)) {
            return false;
        }
        try {
            handler.accept(event);
            store.complete(eventId);
            return true;
        } catch (RuntimeException | Error failure) {
            store.remove(eventId);
            throw failure;
        }
    }
}
