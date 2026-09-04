package com.ganesh.fleetdispatch.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DriverRequest(
        @Min(0) long id,
        @Min(0) long currentNode,
        @NotNull String status
) {
}
