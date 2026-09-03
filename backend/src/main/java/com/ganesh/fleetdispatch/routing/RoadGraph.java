package com.ganesh.fleetdispatch.routing;

import java.util.Collection;
import java.util.Optional;

public interface RoadGraph {
    Optional<RoadNode> node(String nodeId);

    Collection<RoadEdge> outgoingEdges(String nodeId);

    Collection<RoadNode> nodes();
}
