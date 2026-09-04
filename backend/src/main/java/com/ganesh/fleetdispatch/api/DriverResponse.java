package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.DriverDocument;

public record DriverResponse(
        long id,
        long currentNode,
        String status) {

    public static DriverResponse from(DriverDocument driver) {
        return new DriverResponse(driver.id(), driver.currentNode(), driver.status());
    }
}
