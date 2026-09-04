package com.ganesh.fleetdispatch.api;

import java.util.List;

public record OrderResponse(
        long id,
        long pickupNode,
        long dropoffNode,
        long requestTimestamp,
        String status,
        Long assignedDriverId,
        List<Long> route
) {
}
