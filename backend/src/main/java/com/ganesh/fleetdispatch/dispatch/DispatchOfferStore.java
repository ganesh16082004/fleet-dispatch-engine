package com.ganesh.fleetdispatch.dispatch;

import java.util.List;
import java.util.Optional;

/** Thread-safe store for dispatch offers. */
public interface DispatchOfferStore {
    DispatchOffer create(DispatchOffer offer);

    Optional<DispatchOffer> get(long offerId);

    boolean transition(long offerId, DispatchOfferStatus expected, DispatchOfferStatus next);

    List<DispatchOffer> getPendingOffersForOrder(long orderId);

    List<DispatchOffer> getOffersForOrder(long orderId);
}
