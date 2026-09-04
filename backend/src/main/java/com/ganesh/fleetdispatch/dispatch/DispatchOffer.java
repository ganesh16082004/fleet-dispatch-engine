package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;

import java.util.Objects;

/** Immutable driver offer with a stable lifecycle identifier and expiry time. */
public record DispatchOffer(
        long offerId,
        long orderId,
        long driverId,
        NodeId expectedDriverNode,
        Route driverToPickupRoute,
        long createdAtMillis,
        long expiresAtMillis,
        DispatchOfferStatus status
) {
    public DispatchOffer {
        if (offerId < 0 || orderId < 0 || driverId < 0) {
            throw new IllegalArgumentException("IDs must be non-negative");
        }
        Objects.requireNonNull(expectedDriverNode, "expectedDriverNode");
        Objects.requireNonNull(driverToPickupRoute, "driverToPickupRoute");
        Objects.requireNonNull(status, "status");
        if (createdAtMillis < 0 || expiresAtMillis < createdAtMillis) {
            throw new IllegalArgumentException("Invalid offer timestamps");
        }
    }
}
