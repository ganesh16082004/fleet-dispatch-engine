package com.ganesh.fleetdispatch.persistence;

import com.ganesh.fleetdispatch.dispatch.Order;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "orders")
public record OrderDocument(
        @Id Long id,
        long pickupNode,
        long dropoffNode,
        long requestTimestamp,
        String status
) {
    public static OrderDocument from(Order order) {
        return new OrderDocument(
                order.id(),
                order.pickupNode().value(),
                order.dropoffNode().value(),
                order.requestTimestamp(),
                order.status().name());
    }
}
