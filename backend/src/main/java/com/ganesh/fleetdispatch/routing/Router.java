package com.ganesh.fleetdispatch.routing;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;

/** Computes a lowest-cost route between two graph nodes. */
public interface Router {
    Route findRoute(NodeId source, NodeId target);
}
