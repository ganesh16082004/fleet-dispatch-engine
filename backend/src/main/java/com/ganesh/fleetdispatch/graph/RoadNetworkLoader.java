package com.ganesh.fleetdispatch.graph;

/**
 * Loads a road network into the engine's canonical in-memory representation.
 *
 * <p>Routing code depends only on this graph contract and stays independent
 * from the source format (OSM, generated fixtures, database snapshots, etc.).</p>
 */
public interface RoadNetworkLoader {
    RoadGraph load();
}
