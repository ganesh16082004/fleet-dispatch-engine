package com.ganesh.fleetdispatch.dispatch;

import java.util.Optional;

/** Outcome of attempting to re-offer an order after a rejected or expired offer. */
public record ReofferResult(Optional<DispatchOffer> offer, boolean attempted) {
    public ReofferResult {
        offer = offer == null ? Optional.empty() : offer;
    }

    public static ReofferResult offered(DispatchOffer offer) {
        return new ReofferResult(Optional.of(offer), true);
    }

    public static ReofferResult unavailable() {
        return new ReofferResult(Optional.empty(), true);
    }

    public static ReofferResult notEligible() {
        return new ReofferResult(Optional.empty(), false);
    }
}
