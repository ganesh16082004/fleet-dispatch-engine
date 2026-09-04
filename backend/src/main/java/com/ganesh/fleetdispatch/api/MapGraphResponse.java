package com.ganesh.fleetdispatch.api;

import java.util.List;
import java.util.Map;

/** Serialized road-network payload consumed by the live Bengaluru dashboard map. */
public record MapGraphResponse(
        Map<String, Object> roads,
        Map<String, List<Double>> nodes,
        List<Double> center,
        int nodeCount,
        int edgeCount) {
}
