"use client";

import { useEffect, useRef, useState } from "react";
import type { Map as LeafletMap, CircleMarker, LayerGroup, Polyline, Rectangle } from "leaflet";

export type MapDriver = { id: number; currentNode: number; status: string };
export type MapOrder = { id: number; pickupNode: number; dropoffNode: number; status: string; assignedDriverId?: number | null; route?: number[] };
export type MapGraph = {
  roads: GeoJSON.FeatureCollection<GeoJSON.LineString, Record<string, unknown>>;
  nodes: Record<string, [number, number]>;
  center: [number, number];
  bounds: [number, number, number, number];
  nodeCount: number;
  edgeCount: number;
};

export default function FleetMap({
  graph,
  drivers,
  locations,
  orders,
  selectedOrderId,
  onSelectOrder
}: {
  graph: MapGraph | null;
  drivers: MapDriver[];
  locations: Record<number, { nodeId: number; sequenceNumber: number }>;
  orders: MapOrder[];
  selectedOrderId: number | null;
  onSelectOrder: (id: number) => void;
}) {
  const elementRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<LeafletMap | null>(null);
  const driverLayerRef = useRef<LayerGroup | null>(null);
  const routeLayerRef = useRef<LayerGroup | null>(null);
  const boundaryRef = useRef<Rectangle | null>(null);
  const roadsRef = useRef<GeoJSON.GeoJSON | null>(null);
  const graphRef = useRef<MapGraph | null>(graph);
  const [mapReady, setMapReady] = useState(false);

  useEffect(() => { graphRef.current = graph; }, [graph]);

  useEffect(() => {
    let disposed = false;
    import("leaflet").then((L) => {
      if (disposed || !elementRef.current || mapRef.current) return;
      const map = L.map(elementRef.current, {
        zoomControl: false,
        preferCanvas: true,
        minZoom: 9,
        maxZoom: 19
      });
      L.control.zoom({ position: "bottomright" }).addTo(map);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: "© OpenStreetMap contributors"
      }).addTo(map);
      driverLayerRef.current = L.layerGroup().addTo(map);
      routeLayerRef.current = L.layerGroup().addTo(map);
      mapRef.current = map;
      map.setView(graphRef.current?.center ?? [12.9716, 77.5946], graphRef.current ? 12 : 11);
      setMapReady(true);
    });

    return () => {
      disposed = true;
      mapRef.current?.remove();
      mapRef.current = null;
      driverLayerRef.current = null;
      routeLayerRef.current = null;
      boundaryRef.current = null;
      roadsRef.current = null;
      setMapReady(false);
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!mapReady || !map || !graph) return;
    import("leaflet").then((L) => {
      if (!mapRef.current) return;

      roadsRef.current?.remove();
      boundaryRef.current?.remove();

      roadsRef.current = L.geoJSON(graph.roads, {
        style: {
          color: "#56746a",
          opacity: 0.48,
          weight: 1.15,
          lineCap: "round",
          lineJoin: "round"
        }
      }).addTo(map);

      // The boundary comes directly from the minimum/maximum coordinates of the
      // same road graph consumed by Dijkstra/A*. It is not a guessed city box.
      const [minLongitude, minLatitude, maxLongitude, maxLatitude] = graph.bounds;
      boundaryRef.current = L.rectangle(
        [[minLatitude, minLongitude], [maxLatitude, maxLongitude]],
        {
          color: "#7de2b0",
          weight: 1,
          opacity: 0.7,
          fillOpacity: 0.02,
          dashArray: "5 6",
          interactive: false
        }
      ).addTo(map);

      map.fitBounds(
        [[minLatitude, minLongitude], [maxLatitude, maxLongitude]],
        { padding: [18, 18], maxZoom: 14 }
      );
    });
  }, [graph, mapReady]);

  useEffect(() => {
    const layer = driverLayerRef.current;
    const graphData = graphRef.current;
    if (!mapReady || !layer || !graphData) return;

    import("leaflet").then((L) => {
      layer.clearLayers();
      for (const driver of drivers) {
        const nodeId = locations[driver.id]?.nodeId ?? driver.currentNode;
        const coordinate = graphData.nodes[String(nodeId)];
        if (!coordinate) continue;

        const isOffline = driver.status === "OFFLINE";
        const isBusy = driver.status === "BUSY";
        const marker: CircleMarker = L.circleMarker(coordinate, {
          radius: isOffline ? 7 : 7,
          color: "#07110d",
          weight: 2,
          fillColor: isOffline ? "#727e79" : isBusy ? "#d7b158" : "#4fda96",
          fillOpacity: isOffline ? 0.55 : 0.97
        });
        const assignedOrder = orders.find((order) => order.assignedDriverId === driver.id && order.status !== "COMPLETED" && order.status !== "CANCELLED");
        const orderState = assignedOrder ? `\nOrder #${assignedOrder.id} · ${assignedOrder.status.replaceAll("_", " ")}` : "";
        marker.bindTooltip(`<strong>Driver #${driver.id}</strong><br/>${driver.status}<br/>Node ${nodeId}${orderState.replaceAll("\n", "<br/>")}`, { direction: "top", offset: [0, -6] });
        marker.on("click", () => {
          if (assignedOrder) onSelectOrder(assignedOrder.id);
        });
        marker.addTo(layer);
      }
    });
  }, [drivers, locations, orders, onSelectOrder, mapReady]);

  useEffect(() => {
    const layer = routeLayerRef.current;
    const map = mapRef.current;
    const graphData = graphRef.current;
    if (!mapReady || !layer || !map || !graphData) return;

    import("leaflet").then((L) => {
      layer.clearLayers();
      if (selectedOrderId === null) return;
      const order = orders.find((item) => item.id === selectedOrderId);
      if (!order) return;

      const routeCoordinates = (order.route ?? [])
        .map((nodeId) => graphData.nodes[String(nodeId)])
        .filter((value): value is [number, number] => Array.isArray(value));

      if (routeCoordinates.length >= 2) {
        const routeColor = order.status === "RECOVERY_REQUIRED" ? "#e36d64" : order.status === "COMPLETED" ? "#74817d" : "#19b873";
        const route: Polyline = L.polyline(routeCoordinates, {
          color: routeColor,
          weight: 5,
          opacity: 0.88,
          lineCap: "round",
          lineJoin: "round"
        });
        route.addTo(layer);
      }

      const pickup = graphData.nodes[String(order.pickupNode)];
      const dropoff = graphData.nodes[String(order.dropoffNode)];
      if (pickup) L.circleMarker(pickup, { radius: 7, color: "#fff", weight: 2, fillColor: "#398ff0", fillOpacity: 1 }).bindTooltip(`Pickup · Order #${order.id}`).addTo(layer);
      if (dropoff) L.circleMarker(dropoff, { radius: 7, color: "#fff", weight: 2, fillColor: "#e36d64", fillOpacity: 1 }).bindTooltip(`Drop-off · Order #${order.id}`).addTo(layer);
      if (routeCoordinates.length >= 2) map.fitBounds(L.latLngBounds(routeCoordinates), { padding: [45, 45], maxZoom: 15 });
    });
  }, [selectedOrderId, orders, mapReady]);

  return <div ref={elementRef} className="fleet-map-canvas" aria-label="Bengaluru live fleet map using the routing road graph boundary" />;
}
