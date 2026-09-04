package com.ganesh.fleetdispatch.persistence;

import com.ganesh.fleetdispatch.dispatch.Driver;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "drivers")
public record DriverDocument(
        @Id Long id,
        long currentNode,
        String status
) {
    public static DriverDocument from(Driver driver) {
        return new DriverDocument(
                driver.id(),
                driver.currentNode().value(),
                driver.status().name());
    }
}
