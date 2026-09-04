package com.ganesh.fleetdispatch.dispatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Concurrent in-memory dispatch-offer store. */
public final class InMemoryDispatchOfferStore implements DispatchOfferStore {
    private final ConcurrentHashMap<Long, DispatchOffer> offers = new ConcurrentHashMap<>();

    @Override
    public DispatchOffer create(DispatchOffer offer) {
        Objects.requireNonNull(offer, "offer");
        DispatchOffer previous = offers.putIfAbsent(offer.offerId(), offer);
        if (previous != null) {
            throw new IllegalArgumentException("Offer already exists: " + offer.offerId());
        }
        return offer;
    }

    @Override
    public Optional<DispatchOffer> get(long offerId) {
        return Optional.ofNullable(offers.get(offerId));
    }

    @Override
    public boolean transition(
            long offerId,
            DispatchOfferStatus expected,
            DispatchOfferStatus next) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        AtomicBoolean transitioned = new AtomicBoolean(false);
        offers.computeIfPresent(offerId, (id, current) -> {
            if (current.status() != expected) {
                return current;
            }
            transitioned.set(true);
            return new DispatchOffer(
                    current.offerId(),
                    current.orderId(),
                    current.driverId(),
                    current.expectedDriverNode(),
                    current.driverToPickupRoute(),
                    current.createdAtMillis(),
                    current.expiresAtMillis(),
                    next);
        });
        return transitioned.get();
    }

    @Override
    public List<DispatchOffer> getPendingOffersForOrder(long orderId) {
        return getOffersForOrder(orderId).stream()
                .filter(offer -> offer.status() == DispatchOfferStatus.PENDING)
                .toList();
    }

    @Override
    public List<DispatchOffer> getOffersForOrder(long orderId) {
        List<DispatchOffer> result = new ArrayList<>();
        for (DispatchOffer offer : offers.values()) {
            if (offer.orderId() == orderId) {
                result.add(offer);
            }
        }
        result.sort(Comparator.comparingLong(DispatchOffer::offerId));
        return List.copyOf(result);
    }
}
