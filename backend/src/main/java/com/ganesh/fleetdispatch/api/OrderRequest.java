package com.ganesh.fleetdispatch.api;

public record OrderRequest(
        long id,
        long pickupNode,
        long dropoffNode
) {
}
