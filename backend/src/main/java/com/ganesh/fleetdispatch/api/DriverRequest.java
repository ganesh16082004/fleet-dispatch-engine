package com.ganesh.fleetdispatch.api;

public record DriverRequest(
        long id,
        long currentNode,
        String status
) {
}
