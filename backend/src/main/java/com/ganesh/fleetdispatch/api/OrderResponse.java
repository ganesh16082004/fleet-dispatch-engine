package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.OrderDocument;

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
    public static OrderResponse from(OrderDocument order) {
        return new OrderResponse(
                order.id(),
                order.pickupNode(),
                order.dropoffNode(),
                order.requestTimestamp(),
                order.status(),
                order.assignedDriverId(),
                List.of());
    }
}
