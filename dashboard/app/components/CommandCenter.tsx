"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import FleetMap, { type MapGraph } from "./FleetMap";

type Driver = { id: number; currentNode: number; status: string; sequenceNumber?: number; lastHeartbeatMillis?: number };
type Order = { id: number; pickupNode: number; dropoffNode: number; requestTimestamp: number; status: string; assignedDriverId?: number | null; route?: number[] };
type Summary = {
  totalDrivers: number; availableDrivers: number; busyDrivers: number; offlineDrivers: number;
  totalOrders: number; createdOrders: number; offeredOrders: number; assignedOrders: number;
  pickedUpOrders: number; recoveryOrders: number; completedOrders: number; cancelledOrders: number;
  activeOrders: number; pendingOutboxEvents: number; failedOutboxEvents: number;
};
type EventItem = { eventId: string; eventType: string; aggregateId: string; aggregateType: string; createdAt: string; payload?: Record<string, unknown> };
type LiveLocation = { driverId: number; nodeId: number; sequenceNumber: number; timestampMillis: number };
type DashboardGraph = MapGraph & { roads: GeoJSON.FeatureCollection<GeoJSON.LineString, Record<string, unknown>> };

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const DASHBOARD_WS = process.env.NEXT_PUBLIC_DASHBOARD_WS_URL ?? "ws://127.0.0.1:8088/dashboard";
const DRIVER_WS_BASE = process.env.NEXT_PUBLIC_DRIVER_WS_BASE_URL ?? "ws://127.0.0.1:8087/drivers";

const initialSummary: Summary = {
  totalDrivers: 0, availableDrivers: 0, busyDrivers: 0, offlineDrivers: 0,
  totalOrders: 0, createdOrders: 0, offeredOrders: 0, assignedOrders: 0,
  pickedUpOrders: 0, recoveryOrders: 0, completedOrders: 0, cancelledOrders: 0,
  activeOrders: 0, pendingOutboxEvents: 0, failedOutboxEvents: 0
};

const statusClass = (value: string) => value.toLowerCase().replaceAll(" ", "_");
const timeLabel = (value?: string | number | null) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
};

function haversineMeters(a: [number, number], b: [number, number]) {
  const toRad = (value: number) => value * Math.PI / 180;
  const lat1 = toRad(a[0]);
  const lat2 = toRad(b[0]);
  const dLat = toRad(b[0] - a[0]);
  const dLon = toRad(b[1] - a[1]);
  const sinLat = Math.sin(dLat / 2);
  const sinLon = Math.sin(dLon / 2);
  const h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${API_BASE}${path}`, { cache: "no-store" });
    if (!response.ok) return null;
    return await response.json() as T;
  } catch {
    return null;
  }
}

async function post<T = unknown>(path: string, body?: unknown): Promise<T | null> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `${response.status} ${response.statusText}`);
  }
  return response.status === 204 ? null : await response.json() as T;
}

export default function CommandCenter() {
  const [summary, setSummary] = useState<Summary>(initialSummary);
  const [drivers, setDrivers] = useState<Record<number, Driver>>({});
  const [locations, setLocations] = useState<Record<number, LiveLocation>>({});
  const [orders, setOrders] = useState<Order[]>([]);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [graph, setGraph] = useState<DashboardGraph | null>(null);
  const [connected, setConnected] = useState(false);
  const [backendUp, setBackendUp] = useState(false);
  const [lastRefresh, setLastRefresh] = useState<number | null>(null);
  const [scenarioRunning, setScenarioRunning] = useState(false);
  const [scenarioMessage, setScenarioMessage] = useState("Live Bengaluru operations console");
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);
  const [scenarioDriverIds, setScenarioDriverIds] = useState<number[]>([]);

  const socketsRef = useRef<Map<number, WebSocket>>(new Map());
  const sequenceRef = useRef<Map<number, number>>(new Map());
  const telemetryTimerRef = useRef<number | null>(null);
  const scenarioTimersRef = useRef<number[]>([]);
  const scenarioDriverNodesRef = useRef<Map<number, number>>(new Map());
  const graphLoadedRef = useRef(false);
  const orderRoutesRef = useRef<Map<number, number[]>>(new Map());

  const load = useCallback(async () => {
    const healthBody = await fetchJson<{ status?: string }>("/api/v1/health");
    const healthy = healthBody?.status === "UP";
    setBackendUp(healthy);
    const [summaryBody, driversBody, ordersBody, eventsBody] = await Promise.all([
      fetchJson<Summary>("/api/v1/dashboard/summary"),
      fetchJson<Driver[]>("/api/v1/drivers"),
      fetchJson<Order[]>("/api/v1/orders"),
      fetchJson<EventItem[]>("/api/v1/events/recent?limit=40")
    ]);
    if (summaryBody) setSummary(summaryBody);
    if (driversBody) {
      const next: Record<number, Driver> = {};
      for (const driver of driversBody) next[driver.id] = driver;
      setDrivers(next);
    }
    if (ordersBody) {
      const merged = ordersBody.map((order) => ({ ...order, route: order.route?.length ? order.route : orderRoutesRef.current.get(order.id) ?? [] }));
      setOrders(merged);
      setSelectedOrderId((current) => current ?? merged.find((order) => ["ASSIGNED", "PICKED_UP", "RECOVERY_REQUIRED"].includes(order.status))?.id ?? null);
    }
    if (eventsBody) setEvents(eventsBody);
    if (!graphLoadedRef.current) {
      const graphBody = await fetchJson<DashboardGraph>("/api/v1/map/geojson");
      if (graphBody) {
        setGraph(graphBody);
        graphLoadedRef.current = true;
      }
    }
    if (healthy && scenarioMessage.startsWith("Backend unavailable")) setScenarioMessage("Live Bengaluru operations console");
    if (!healthy) setScenarioMessage("Backend unavailable — check Spring Boot on :8080");
    setLastRefresh(Date.now());
  }, [scenarioMessage]);

  useEffect(() => {
    void load();
    const interval = window.setInterval(() => void load(), 3000);
    return () => window.clearInterval(interval);
  }, [load]);

  useEffect(() => {
    let socket: WebSocket | null = null;
    let retry: number | null = null;
    let stopped = false;
    const connect = () => {
      if (stopped) return;
      try {
        socket = new WebSocket(DASHBOARD_WS);
        socket.onopen = () => setConnected(true);
        socket.onclose = () => {
          setConnected(false);
          if (!stopped) retry = window.setTimeout(connect, 2500);
        };
        socket.onerror = () => setConnected(false);
        socket.onmessage = (message) => {
          try {
            const packet = JSON.parse(message.data as string) as { type?: string; drivers?: Driver[] } & LiveLocation;
            const snapshotDrivers = packet.drivers;
            if (packet.type === "snapshot" && Array.isArray(snapshotDrivers)) {
              setDrivers((current) => ({ ...current, ...Object.fromEntries(snapshotDrivers.map((driver) => [driver.id, driver])) }));
            }
            if (packet.type === "location" || typeof packet.driverId === "number") {
              const live: LiveLocation = {
                driverId: Number(packet.driverId),
                nodeId: Number(packet.nodeId),
                sequenceNumber: Number(packet.sequenceNumber ?? 0),
                timestampMillis: Number(packet.timestampMillis ?? Date.now())
              };
              setLocations((current) => {
                const previous = current[live.driverId];
                if (previous && live.sequenceNumber <= previous.sequenceNumber) return current;
                return { ...current, [live.driverId]: live };
              });
            }
          } catch {
            // Ignore malformed packets.
          }
        };
      } catch {
        setConnected(false);
      }
    };
    connect();
    return () => {
      stopped = true;
      if (retry) window.clearTimeout(retry);
      socket?.close();
    };
  }, []);

  useEffect(() => () => {
    if (telemetryTimerRef.current) window.clearInterval(telemetryTimerRef.current);
    for (const timer of scenarioTimersRef.current) window.clearTimeout(timer);
    for (const socket of socketsRef.current.values()) socket.close();
  }, []);

  const roadPairs = useMemo(() => {
    if (!graph) return [] as Array<[number, number]>;
    return graph.roads.features.map((feature) => {
      const properties = feature.properties as Record<string, unknown>;
      const from = Number(properties.from);
      const to = Number(properties.to);
      return Number.isFinite(from) && Number.isFinite(to) ? [from, to] as [number, number] : null;
    }).filter((pair): pair is [number, number] => pair !== null);
  }, [graph]);

  const driverList = useMemo(() => {
    const scenarioSet = new Set(scenarioDriverIds);
    const selected = scenarioDriverIds.length > 0
      ? Object.values(drivers).filter((driver) => scenarioSet.has(driver.id))
      : Object.values(drivers);
    return [...selected].sort((a, b) => {
      const ai = scenarioDriverIds.indexOf(a.id);
      const bi = scenarioDriverIds.indexOf(b.id);
      return (ai < 0 ? 999 : ai) - (bi < 0 ? 999 : bi) || b.id - a.id;
    });
  }, [drivers, scenarioDriverIds]);

  const visibleOrders = useMemo(() => [...orders].sort((a, b) => b.requestTimestamp - a.requestTimestamp).slice(0, 14), [orders]);
  const activeDrivers = summary.totalDrivers ? summary.totalDrivers - summary.offlineDrivers : driverList.filter((driver) => driver.status !== "OFFLINE").length;
  const utilization = summary.totalDrivers ? Math.round((summary.busyDrivers / summary.totalDrivers) * 100) : 0;
  const selectedOrder = orders.find((order) => order.id === selectedOrderId) ?? null;

  const stopTelemetry = () => {
    if (telemetryTimerRef.current) window.clearInterval(telemetryTimerRef.current);
    telemetryTimerRef.current = null;
    for (const socket of socketsRef.current.values()) socket.close();
    socketsRef.current.clear();
    sequenceRef.current.clear();
  };

  const startTelemetry = (ids: number[]) => {
    stopTelemetry();
    for (const id of ids) {
      const socket = new WebSocket(`${DRIVER_WS_BASE}/${id}`);
      socketsRef.current.set(id, socket);
      sequenceRef.current.set(id, 0);
    }
    telemetryTimerRef.current = window.setInterval(() => {
      for (const id of ids) {
        const socket = socketsRef.current.get(id);
        if (!socket || socket.readyState !== WebSocket.OPEN) continue;
        const seq = (sequenceRef.current.get(id) ?? 0) + 1;
        sequenceRef.current.set(id, seq);
        const nodeId = scenarioDriverNodesRef.current.get(id);
        if (nodeId === undefined) continue;
        socket.send(JSON.stringify({ sequenceNumber: seq, nodeId, timestampMillis: Date.now() }));
      }
    }, 900);
  };

  const runLiveScenario = async () => {
    if (scenarioRunning || !graph || roadPairs.length < 1) return;
    setScenarioRunning(true);
    stopTelemetry();
    for (const timer of scenarioTimersRef.current) window.clearTimeout(timer);
    scenarioTimersRef.current = [];
    scenarioDriverNodesRef.current.clear();

    const persistedBusyCoordinates = Object.values(drivers)
      .filter((driver) => driver.status === "BUSY")
      .map((driver) => graph.nodes[String(driver.currentNode)])
      .filter((coordinate): coordinate is [number, number] => Array.isArray(coordinate));
    const persistedOccupiedNodes = new Set(
      Object.values(drivers)
        .map((driver) => driver.currentNode)
        .filter((node) => Number.isFinite(node))
    );

    const step = Math.max(1, Math.ceil(roadPairs.length / 8_000));
    let primaryPair: [number, number] | null = null;
    for (let index = 0; index < roadPairs.length; index += step) {
      const pair = roadPairs[index];
      if (persistedOccupiedNodes.has(pair[0])) continue;
      const coordinate = graph.nodes[String(pair[0])];
      if (!coordinate) continue;
      const nearestBusyDistance = persistedBusyCoordinates.length === 0
        ? Number.POSITIVE_INFINITY
        : Math.min(...persistedBusyCoordinates.map((busyCoordinate) => haversineMeters(coordinate, busyCoordinate)));
      if (nearestBusyDistance > 2_500) {
        primaryPair = pair;
        break;
      }
    }
    if (!primaryPair) {
      setScenarioMessage("Scenario stopped: could not find a clean road-graph pickup zone for the demo");
      setScenarioRunning(false);
      return;
    }

    const demoNode = primaryPair[0];
    const base = Math.floor(Date.now() / 1000) * 10;
    const ids = Array.from({ length: 6 }, (_, index) => base + index + 1);
    ids.forEach((id) => scenarioDriverNodesRef.current.set(id, demoNode));
    setScenarioDriverIds(ids);
    const primaryOrderId = Math.floor(Date.now() / 1000) + 900000;

    try {
      setScenarioMessage("1 / 6 · registering six demo drivers at the pickup node…");
      for (const id of ids) {
        await post("/api/v1/drivers", { id, currentNode: demoNode, status: "AVAILABLE" });
      }
      setScenarioMessage("2 / 6 · creating order from the live road graph…");
      const created = await post<Order>("/api/v1/orders", { id: primaryOrderId, pickupNode: primaryPair[0], dropoffNode: primaryPair[1] });
      if (!created) throw new Error("Primary order was not created");

      setScenarioMessage("3 / 6 · real dispatch selecting one of the six demo drivers…");
      const assigned = await post<Order>(`/api/v1/orders/${primaryOrderId}/dispatch`);
      if (!assigned?.assignedDriverId) throw new Error("No feasible driver was found for the demo order");
      if (!ids.includes(assigned.assignedDriverId)) {
        throw new Error(`Dispatch selected unexpected persisted driver #${assigned.assignedDriverId}`);
      }
      const failedDriverId = assigned.assignedDriverId;
      const failedNodeId = demoNode;
      if (assigned.route?.length) orderRoutesRef.current.set(primaryOrderId, assigned.route);
      startTelemetry(ids.filter((id) => id !== failedDriverId));
      await load();
      setSelectedOrderId(primaryOrderId);

      scenarioTimersRef.current.push(window.setTimeout(async () => {
        try {
          setScenarioMessage(`4 / 6 · Driver #${failedDriverId} → PICKED UP`);
          const pickup = await post<Order>(`/api/v1/orders/${primaryOrderId}/pickup`);
          if (pickup?.route?.length) orderRoutesRef.current.set(primaryOrderId, pickup.route);
          await load();
        } catch (error) {
          setScenarioMessage(error instanceof Error ? error.message : "Pickup failed");
        }
      }, 3500));

      scenarioTimersRef.current.push(window.setTimeout(async () => {
        try {
          setScenarioMessage(`5 / 6 · Driver #${failedDriverId} failed → recovery at Node ${failedNodeId}`);
          await post(`/api/v1/recovery/drivers/${failedDriverId}/fail`);
          await load();
        } catch (error) {
          setScenarioMessage(error instanceof Error ? error.message : "Recovery trigger failed");
        }
      }, 7500));

      scenarioTimersRef.current.push(window.setTimeout(async () => {
        try {
          let latest: Order | null = null;
          for (let attempt = 0; attempt < 16; attempt++) {
            latest = await fetchJson<Order>(`/api/v1/orders/${primaryOrderId}`);
            if (latest?.status === "ASSIGNED" && latest.assignedDriverId && latest.assignedDriverId !== failedDriverId) break;
            await new Promise((resolve) => window.setTimeout(resolve, 500));
          }
          if (!latest?.assignedDriverId || latest.assignedDriverId === failedDriverId) {
            throw new Error("Recovery worker did not assign a replacement driver");
          }
          const replacementId = latest.assignedDriverId;
          setScenarioMessage(`5 / 6 · HANDOFF → replacement Driver #${replacementId}`);
          await load();
          scenarioTimersRef.current.push(window.setTimeout(async () => {
            try {
              setScenarioMessage(`6 / 6 · replacement Driver #${replacementId} picked up → completing…`);
              const pickup = await post<Order>(`/api/v1/orders/${primaryOrderId}/pickup`);
              if (pickup?.route?.length) orderRoutesRef.current.set(primaryOrderId, pickup.route);
              await new Promise((resolve) => window.setTimeout(resolve, 1200));
              await post<Order>(`/api/v1/orders/${primaryOrderId}/complete`);
              setScenarioMessage(`COMPLETE · Order #${primaryOrderId} delivered by replacement Driver #${replacementId}`);
              stopTelemetry();
              await load();
            } catch (error) {
              setScenarioMessage(error instanceof Error ? error.message : "Recovery completion failed");
            }
          }, 1800));
        } catch (error) {
          setScenarioMessage(error instanceof Error ? error.message : "Replacement assignment pending");
        }
      }, 9500));
    } catch (error) {
      setScenarioMessage(error instanceof Error ? error.message : "Scenario failed");
    } finally {
      setScenarioRunning(false);
    }
  };

  const actOnOrder = async (order: Order, action: "dispatch" | "pickup" | "complete" | "cancel") => {
    try {
      const response = await post<Order>(`/api/v1/orders/${order.id}/${action}`);
      if (response?.route?.length) orderRoutesRef.current.set(order.id, response.route);
      if (response) setOrders((current) => current.map((item) => item.id === response.id ? { ...response, route: response.route?.length ? response.route : orderRoutesRef.current.get(response.id) ?? item.route ?? [] } : item));
      setSelectedOrderId(order.id);
      await load();
      setScenarioMessage(`${action.toUpperCase()} complete · Order #${order.id}`);
    } catch (error) {
      setScenarioMessage(error instanceof Error ? error.message : `${action} failed`);
    }
  };

  const eventCopy = (event: EventItem) => {
    const payload = event.payload ?? {};
    const subject = event.aggregateType === "DRIVER" ? `Driver #${event.aggregateId.replace("driver-", "")}` : `Order #${event.aggregateId.replace("order-", "")}`;
    const state = String(payload.status ?? event.eventType).replaceAll("_", " ");
    return `${subject} · ${state}`;
  };

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand"><div className="logo"><div className="logo-mark" /></div><div><div className="eyebrow">BENGALURU OPERATIONS</div><h1>Fleet Dispatch Command Center</h1><div className="subtitle">Live routing · intelligent dispatch · recovery orchestration</div></div></div>
        <div className="top-actions">
          <div className={`system-pill ${backendUp ? "ok" : "bad"}`}><span />{backendUp ? "SYSTEM OPERATIONAL" : "BACKEND OFFLINE"}</div>
          <div className={`system-pill ${connected ? "ok" : "bad"}`}><span />{connected ? "LIVE TELEMETRY" : "API ONLY"}</div>
          <button className="scenario-button" onClick={() => void runLiveScenario()} disabled={scenarioRunning || !graph || roadPairs.length === 0}>{scenarioRunning ? "Scenario running…" : "▶ Run full lifecycle"}</button>
        </div>
      </header>
      <div className={`scenario-banner ${scenarioRunning ? "running" : ""}`}><div className="scenario-message"><span className="scenario-pulse" />{scenarioMessage}</div><div className="map-meta">{graph ? `${graph.nodeCount.toLocaleString()} nodes · ${graph.edgeCount.toLocaleString()} road segments` : "Loading road network…"}</div><button onClick={() => void load()}>Refresh</button></div>
      <section className="kpis">
        <div className="card metric"><div className="metric-label">Fleet health</div><div className="metric-value">{activeDrivers}</div><div className="metric-foot"><b>{summary.availableDrivers}</b> available · <b>{summary.busyDrivers}</b> busy · <b>{summary.offlineDrivers}</b> offline</div></div>
        <div className="card metric"><div className="metric-label">Orders in motion</div><div className="metric-value">{summary.activeOrders}</div><div className="metric-foot"><b>{summary.assignedOrders}</b> assigned · <b>{summary.pickedUpOrders}</b> picked up</div></div>
        <div className="card metric"><div className="metric-label">Fleet utilization</div><div className="metric-value">{utilization}%</div><div className="metric-foot"><b>{summary.completedOrders}</b> completed · {summary.cancelledOrders} cancelled</div></div>
        <div className={`card metric ${summary.recoveryOrders > 0 ? "alert" : ""}`}><div className="metric-label">Recovery queue</div><div className="metric-value">{summary.recoveryOrders}</div><div className="metric-foot"><b>{summary.failedOutboxEvents}</b> failed outbox · <b>{summary.pendingOutboxEvents}</b> pending</div></div>
      </section>
      <section className="workspace">
        <article className="card map-card"><div className="card-head"><div><div className="section-kicker">LIVE CITY VIEW</div><h2>Fleet position · Bengaluru</h2><div className="card-hint">Real OpenStreetMap geography + the same road graph used by the routing engine</div></div><div className="head-stat"><span className="tiny-dot" />{graph ? "ROAD NETWORK ONLINE" : "LOADING MAP"}</div></div><FleetMap graph={graph} drivers={driverList} locations={locations} orders={orders} selectedOrderId={selectedOrderId} onSelectOrder={setSelectedOrderId} /></article>
        <aside className="right-column">
          <article className="card panel driver-panel"><div className="card-head"><div><div className="section-kicker">FLEET CONTROL</div><h2>Driver operations</h2><div className="card-hint">Heartbeat · node · assignment state</div></div><span className="counter">{driverList.length}</span></div><div className="table-wrap"><table className="table"><thead><tr><th>Driver</th><th>Status</th><th>Node</th><th>Pulse</th></tr></thead><tbody>{driverList.length === 0 ? <tr><td colSpan={4} className="empty">No drivers registered</td></tr> : driverList.map((driver, index) => { const live = locations[driver.id]; return <tr key={`driver-${driver.id}-${index}`}><td className="strong">#{driver.id}</td><td><span className={`status ${statusClass(driver.status)}`}>{driver.status}</span></td><td>{live?.nodeId ?? driver.currentNode}</td><td className="muted">{live ? timeLabel(live.timestampMillis) : "—"}</td></tr>; })}</tbody></table></div></article>
          <article className="card panel events-panel"><div className="card-head"><div><div className="section-kicker">EVENT BUS</div><h2>Operational event stream</h2><div className="card-hint">Kafka-backed history · latest state changes</div></div><span className="counter">{events.length}</span></div><div className="events">{events.length === 0 ? <div className="empty">Waiting for events…</div> : events.slice(0, 18).map((event, index) => <div className="event" key={`${event.eventId ?? "event"}-${event.aggregateId ?? "aggregate"}-${index}`}><div className="event-time">{timeLabel(event.createdAt)}</div><div><div className="event-type">{event.eventType.replaceAll("_", " ")}</div><div className="event-copy">{eventCopy(event)}</div></div></div>)}</div></article>
        </aside>
      </section>
      <section className="card orders-card"><div className="card-head"><div><div className="section-kicker">DISPATCH WORKBENCH</div><h2>Orders in the network</h2><div className="card-hint">Select an order to focus its route, pickup and drop-off on the live map</div></div><div className="head-stat">Updated {timeLabel(lastRefresh)}</div></div><div className="order-layout"><div className="orders-scroll"><table className="table orders-table"><thead><tr><th>Order</th><th>Route</th><th>Status</th><th>Driver</th><th>Actions</th></tr></thead><tbody>{visibleOrders.length === 0 ? <tr><td colSpan={5} className="empty">No orders in the network</td></tr> : visibleOrders.map((order, index) => { const selected = selectedOrderId === order.id; return <tr key={`order-${order.id}-${index}`} className={selected ? "selected-row" : ""} onClick={() => setSelectedOrderId(order.id)}><td className="strong">#{order.id}</td><td>#{order.pickupNode} → #{order.dropoffNode}</td><td><span className={`order-status ${statusClass(order.status)}`}>{order.status.replaceAll("_", " ")}</span></td><td>{order.assignedDriverId ? `#${order.assignedDriverId}` : "—"}</td><td className="actions" onClick={(event) => event.stopPropagation()}>{["CREATED", "OFFERED"].includes(order.status) ? <button className="mini-button" onClick={() => void actOnOrder(order, "dispatch")}>Dispatch</button> : null}{order.status === "ASSIGNED" ? <button className="mini-button" onClick={() => void actOnOrder(order, "pickup")}>Pickup</button> : null}{order.status === "PICKED_UP" ? <button className="mini-button" onClick={() => void actOnOrder(order, "complete")}>Complete</button> : null}{["CREATED", "OFFERED", "ASSIGNED"].includes(order.status) ? <button className="mini-button danger" onClick={() => void actOnOrder(order, "cancel")}>Cancel</button> : null}</td></tr>; })}</tbody></table></div><div className="order-detail">{selectedOrder ? <><div className="detail-label">SELECTED ORDER</div><div className="detail-title">#{selectedOrder.id}</div><div className="detail-status"><span className={`order-status ${statusClass(selectedOrder.status)}`}>{selectedOrder.status.replaceAll("_", " ")}</span></div><div className="detail-grid"><div><span>Pickup</span><b>#{selectedOrder.pickupNode}</b></div><div><span>Drop-off</span><b>#{selectedOrder.dropoffNode}</b></div><div><span>Driver</span><b>{selectedOrder.assignedDriverId ? `#${selectedOrder.assignedDriverId}` : "Unassigned"}</b></div><div><span>Route nodes</span><b>{selectedOrder.route?.length ?? 0}</b></div></div><div className="detail-note">This panel follows the real order state returned by Spring Boot. During recovery, the driver ID changes to the replacement selected by the backend recovery worker.</div></> : <div className="empty detail-empty">Select an order to inspect its route.</div>}</div></div></section>
      <footer className="footer-row"><div><span className="dot-row"><span className={`small-dot ${backendUp ? "green" : "red"}`} />Backend {backendUp ? "healthy" : "offline"}</span><span className="dot-row"><span className={`small-dot ${connected ? "green" : "red"}`} />Telemetry {connected ? "connected" : "disconnected"}</span></div><div>Spring Boot :8080 · Dashboard :3000/:3001 · Bengaluru road graph</div></footer>
    </main>
  );
}
