"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import FleetMap, { type MapGraph } from "./components/FleetMap";

type Driver = { id: number; currentNode: number; status: string; sequenceNumber?: number; lastHeartbeatMillis?: number };
type Order = { id: number; pickupNode: number; dropoffNode: number; requestTimestamp: number; status: string; assignedDriverId?: number | null; route?: number[] };
type Summary = {
  totalDrivers: number; availableDrivers: number; busyDrivers: number; offlineDrivers: number;
  totalOrders: number; createdOrders: number; offeredOrders: number; assignedOrders: number;
  pickedUpOrders: number; recoveryOrders: number; completedOrders: number; cancelledOrders: number;
  activeOrders: number; pendingOutboxEvents: number; failedOutboxEvents: number;
};
type EventItem = { eventId: string; eventType: string; aggregateId: string; aggregateType: string; createdAt: string; publishedAt?: string | null; attempts: number; status: string; payload?: Record<string, unknown> };
type LiveLocation = { driverId: number; nodeId: number; sequenceNumber: number; timestampMillis: number };
type DriverMap = Record<number, Driver>;
type LocationMap = Record<number, LiveLocation>;

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

async function post(path: string, body?: unknown) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `${response.status} ${response.statusText}`);
  }
  return response.status === 204 ? null : response.json();
}

export default function Home() {
  const [summary, setSummary] = useState<Summary>(initialSummary);
  const [drivers, setDrivers] = useState<DriverMap>({});
  const [locations, setLocations] = useState<LocationMap>({});
  const [orders, setOrders] = useState<Order[]>([]);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [graph, setGraph] = useState<MapGraph | null>(null);
  const [connected, setConnected] = useState(false);
  const [backendUp, setBackendUp] = useState(false);
  const [lastRefresh, setLastRefresh] = useState<number | null>(null);
  const [scenarioRunning, setScenarioRunning] = useState(false);
  const [scenarioMessage, setScenarioMessage] = useState("Live Bengaluru operations console");
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);
  const socketsRef = useRef<Map<number, WebSocket>>(new Map());
  const sequenceRef = useRef<Map<number, number>>(new Map());
  const timerRef = useRef<number | null>(null);
  const failureTimersRef = useRef<number[]>([]);

  const load = useCallback(async () => {
    try {
      const [healthResponse, summaryResponse, driversResponse, ordersResponse, eventsResponse, graphResponse] = await Promise.all([
        fetch(`${API_BASE}/actuator/health/readiness`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/dashboard/summary`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/drivers`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/orders`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/events/recent?limit=40`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/map/geojson`, { cache: "no-store" })
      ]);

      setBackendUp(healthResponse.ok);
      if (summaryResponse.ok) setSummary(await summaryResponse.json());
      if (driversResponse.ok) {
        const body = (await driversResponse.json()) as Driver[];
        const next: DriverMap = {};
        for (const driver of body) next[driver.id] = driver;
        setDrivers(next);
      }
      if (ordersResponse.ok) {
        const body = (await ordersResponse.json()) as Order[];
        setOrders(body);
        setSelectedOrderId((current) => current ?? body.find((order) => order.status === "ASSIGNED")?.id ?? null);
      }
      if (eventsResponse.ok) setEvents(await eventsResponse.json());
      if (graphResponse.ok) setGraph(await graphResponse.json());
      setLastRefresh(Date.now());
    } catch {
      setBackendUp(false);
      setScenarioMessage("Backend unavailable — check Spring Boot on :8080");
    }
  }, []);

  useEffect(() => {
    load();
    const interval = window.setInterval(load, 3000);
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
            if (packet.type === "snapshot" && Array.isArray(packet.drivers)) {
              setDrivers((current) => {
                const next = { ...current };
                for (const driver of packet.drivers!) next[driver.id] = { ...next[driver.id], ...driver };
                return next;
              });
              return;
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
            // Ignore malformed telemetry packets.
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
    if (timerRef.current) window.clearInterval(timerRef.current);
    for (const timer of failureTimersRef.current) window.clearTimeout(timer);
    for (const socket of socketsRef.current.values()) socket.close();
  }, []);

  const driverList = useMemo(() => Object.values(drivers).sort((a, b) => a.id - b.id), [drivers]);
  const visibleOrders = useMemo(() => [...orders].sort((a, b) => b.requestTimestamp - a.requestTimestamp).slice(0, 14), [orders]);
  const activeDrivers = summary.totalDrivers ? summary.totalDrivers - summary.offlineDrivers : driverList.filter((driver) => driver.status !== "OFFLINE").length;
  const utilization = summary.totalDrivers ? Math.round((summary.busyDrivers / summary.totalDrivers) * 100) : 0;
  const selectedOrder = orders.find((order) => order.id === selectedOrderId) ?? null;
  const graphNodeIds = useMemo(() => graph ? Object.keys(graph.nodes).map(Number).sort((a, b) => a - b) : [], [graph]);

  const stopDriverStreams = () => {
    for (const socket of socketsRef.current.values()) socket.close();
    socketsRef.current.clear();
    sequenceRef.current.clear();
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
  };

  const startDriverStreams = (driverIds: number[], failedDriverId: number) => {
    stopDriverStreams();
    for (const id of driverIds) {
      const socket = new WebSocket(`${DRIVER_WS_BASE}/${id}`);
      socket.onopen = () => setConnected(true);
      socketsRef.current.set(id, socket);
      sequenceRef.current.set(id, 0);
    }

    window.setTimeout(() => {
      timerRef.current = window.setInterval(() => {
        for (const id of driverIds) {
          if (id === failedDriverId) continue;
          const socket = socketsRef.current.get(id);
          if (!socket || socket.readyState !== WebSocket.OPEN) continue;
          const sequence = (sequenceRef.current.get(id) ?? 0) + 1;
          sequenceRef.current.set(id, sequence);
          const nodeIndex = (driverIds.indexOf(id) * 9 + sequence * 2) % Math.max(graphNodeIds.length, 1);
          const nodeId = graphNodeIds[nodeIndex] ?? 101;
          socket.send(JSON.stringify({ sequenceNumber: sequence, nodeId, timestampMillis: Date.now() }));
        }
      }, 900);
    }, 700);
  };

  const runLiveScenario = async () => {
    if (scenarioRunning || !graph || graphNodeIds.length < 30) return;
    setScenarioRunning(true);
    setScenarioMessage("Provisioning a Bengaluru fleet…");
    stopDriverStreams();
    failureTimersRef.current.forEach((timer) => window.clearTimeout(timer));
    failureTimersRef.current = [];

    const base = 40000 + Math.floor(Date.now() / 1000) % 10000;
    const driverIds = Array.from({ length: 7 }, (_, index) => base + index);
    const failedDriverId = driverIds[3];
    const chosenNodes = driverIds.map((_, index) => graphNodeIds[(index * 37) % graphNodeIds.length]);
    const orderBase = 700000 + (base % 80000);

    try {
      for (let index = 0; index < driverIds.length; index++) {
        try {
          await post("/api/v1/drivers", { id: driverIds[index], currentNode: chosenNodes[index], status: "AVAILABLE" });
        } catch {
          // Scenario IDs are unique; an existing record is harmless.
        }
      }

      for (let index = 0; index < 10; index++) {
        const pickupIndex = (index * 23 + 11) % graphNodeIds.length;
        const dropoffIndex = (index * 31 + 57) % graphNodeIds.length;
        const id = orderBase + index;
        try {
          await post("/api/v1/orders", {
            id,
            pickupNode: graphNodeIds[pickupIndex],
            dropoffNode: graphNodeIds[dropoffIndex]
          });
          try { await post(`/api/v1/orders/${id}/dispatch`); } catch { /* assignment may legitimately be unavailable */ }
        } catch {
          // Ignore individual duplicate/conflict records and continue the scenario.
        }
      }

      setScenarioMessage(`LIVE · Bengaluru fleet active · Driver #${failedDriverId} will stop reporting in 14s`);
      startDriverStreams(driverIds, failedDriverId);
      await load();
      failureTimersRef.current.push(window.setTimeout(() => load(), 10000));
      failureTimersRef.current.push(window.setTimeout(() => {
        setScenarioMessage(`RECOVERY WINDOW · Driver #${failedDriverId} heartbeat lost · watch reassignment`);
        load();
      }, 15000));
      failureTimersRef.current.push(window.setTimeout(() => {
        setScenarioMessage("RECOVERY COMPLETE · fleet state synchronized");
        load();
      }, 24000));
    } finally {
      setScenarioRunning(false);
    }
  };

  const actOnOrder = async (order: Order, action: "dispatch" | "pickup" | "complete" | "cancel") => {
    try {
      await post(`/api/v1/orders/${order.id}/${action}`);
      setSelectedOrderId(order.id);
      await load();
    } catch (error) {
      setScenarioMessage(error instanceof Error ? error.message : "Order action failed");
    }
  };

  const eventCopy = (event: EventItem) => {
    const payload = event.payload ?? {};
    const state = String(payload.status ?? event.eventType).replaceAll("_", " ");
    return `${event.aggregateType === "DRIVER" ? "Driver" : "Order"} #${event.aggregateId} · ${state}`;
  };

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand">
          <div className="logo"><div className="logo-mark" /></div>
          <div>
            <div className="eyebrow">BENGALURU OPERATIONS</div>
            <h1>Fleet Dispatch Command Center</h1>
            <div className="subtitle">Live routing · intelligent dispatch · recovery orchestration</div>
          </div>
        </div>
        <div className="top-actions">
          <div className={`system-pill ${backendUp ? "ok" : "bad"}`}><span />{backendUp ? "SYSTEM OPERATIONAL" : "BACKEND OFFLINE"}</div>
          <div className={`system-pill ${connected ? "ok" : "bad"}`}><span />{connected ? "LIVE TELEMETRY" : "API ONLY"}</div>
          <button className="scenario-button" onClick={runLiveScenario} disabled={scenarioRunning || !graph}>{scenarioRunning ? "Scenario running…" : "▶ Run dispatch scenario"}</button>
        </div>
      </header>

      <div className={`scenario-banner ${scenarioRunning ? "running" : ""}`}>
        <div className="scenario-message"><span className="scenario-pulse" />{scenarioMessage}</div>
        <div className="map-meta">{graph ? `${graph.nodeCount.toLocaleString()} nodes · ${graph.edgeCount.toLocaleString()} road segments` : "Loading road network…"}</div>
        <button onClick={load}>Refresh</button>
      </div>

      <section className="kpis">
        <div className="card metric"><div className="metric-label">Fleet health</div><div className="metric-value">{activeDrivers}</div><div className="metric-foot"><b>{summary.availableDrivers}</b> available · <b>{summary.busyDrivers}</b> busy · <b>{summary.offlineDrivers}</b> offline</div></div>
        <div className="card metric"><div className="metric-label">Orders in motion</div><div className="metric-value">{summary.activeOrders}</div><div className="metric-foot"><b>{summary.assignedOrders}</b> assigned · <b>{summary.pickedUpOrders}</b> picked up</div></div>
        <div className="card metric"><div className="metric-label">Fleet utilization</div><div className="metric-value">{utilization}%</div><div className="metric-foot"><b>{summary.completedOrders}</b> completed · {summary.cancelledOrders} cancelled</div></div>
        <div className={`card metric ${summary.recoveryOrders > 0 ? "alert" : ""}`}><div className="metric-label">Recovery queue</div><div className="metric-value">{summary.recoveryOrders}</div><div className="metric-foot"><b>{summary.failedOutboxEvents}</b> failed outbox · <b>{summary.pendingOutboxEvents}</b> pending</div></div>
      </section>

      <section className="workspace">
        <article className="card map-card">
          <div className="card-head">
            <div><div className="section-kicker">LIVE CITY VIEW</div><h2>Fleet position · Bengaluru</h2><div className="card-hint">Real OpenStreetMap geography + the same road graph used by the routing engine</div></div>
            <div className="head-stat"><span className="tiny-dot" />{graph ? "ROAD NETWORK ONLINE" : "LOADING MAP"}</div>
          </div>
          <FleetMap
            graph={graph}
            drivers={driverList}
            locations={locations}
            orders={orders}
            selectedOrderId={selectedOrderId}
            onSelectOrder={setSelectedOrderId}
          />
        </article>

        <aside className="right-column">
          <article className="card panel driver-panel">
            <div className="card-head"><div><div className="section-kicker">FLEET CONTROL</div><h2>Driver operations</h2><div className="card-hint">Heartbeat · node · assignment state</div></div><span className="counter">{driverList.length}</span></div>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Driver</th><th>Status</th><th>Node</th><th>Pulse</th></tr></thead>
                <tbody>
                  {driverList.length === 0 ? <tr><td colSpan={4} className="empty">No drivers registered</td></tr> : driverList.slice(0, 12).map((driver) => {
                    const live = locations[driver.id];
                    const pulse = live ? timeLabel(live.timestampMillis) : "—";
                    return <tr key={driver.id}><td className="strong">#{driver.id}</td><td><span className={`status ${statusClass(driver.status)}`}>{driver.status}</span></td><td>{live?.nodeId ?? driver.currentNode}</td><td className="muted">{pulse}</td></tr>;
                  })}
                </tbody>
              </table>
            </div>
          </article>

          <article className="card panel events-panel">
            <div className="card-head"><div><div className="section-kicker">EVENT BUS</div><h2>Operational event stream</h2><div className="card-hint">Kafka-backed history · latest state changes</div></div><span className="counter">{events.length}</span></div>
            <div className="events">
              {events.length === 0 ? <div className="empty">Waiting for events…</div> : events.slice(0, 18).map((event) => <div className="event" key={event.eventId}><div className="event-time">{timeLabel(event.createdAt)}</div><div><div className="event-type">{event.eventType.replaceAll("_", " ")}</div><div className="event-copy">{eventCopy(event)}</div></div></div>)}
            </div>
          </article>
        </aside>
      </section>

      <section className="card orders-card">
        <div className="card-head"><div><div className="section-kicker">DISPATCH WORKBENCH</div><h2>Orders in the network</h2><div className="card-hint">Select an order to focus its route, pickup and drop-off on the live map</div></div><div className="head-stat">Updated {timeLabel(lastRefresh)}</div></div>
        <div className="order-layout">
          <div className="orders-scroll">
            <table className="table orders-table">
              <thead><tr><th>Order</th><th>Route</th><th>Status</th><th>Driver</th><th>Actions</th></tr></thead>
              <tbody>
                {visibleOrders.length === 0 ? <tr><td colSpan={5} className="empty">No orders in the network</td></tr> : visibleOrders.map((order) => {
                  const selected = selectedOrderId === order.id;
                  return <tr key={order.id} className={selected ? "selected-row" : ""} onClick={() => setSelectedOrderId(order.id)}><td className="strong">#{order.id}</td><td>#{order.pickupNode} → #{order.dropoffNode}</td><td><span className={`order-status ${statusClass(order.status)}`}>{order.status.replaceAll("_", " ")}</span></td><td>{order.assignedDriverId ? `#${order.assignedDriverId}` : "—"}</td><td className="actions" onClick={(event) => event.stopPropagation()}>{order.status === "CREATED" || order.status === "OFFERED" ? <button className="mini-button" onClick={() => actOnOrder(order, "dispatch")}>Dispatch</button> : null}{order.status === "ASSIGNED" ? <button className="mini-button" onClick={() => actOnOrder(order, "pickup")}>Pickup</button> : null}{order.status === "PICKED_UP" ? <button className="mini-button" onClick={() => actOnOrder(order, "complete")}>Complete</button> : null}{["CREATED","OFFERED","ASSIGNED"].includes(order.status) ? <button className="mini-button danger" onClick={() => actOnOrder(order, "cancel")}>Cancel</button> : null}</td></tr>;
                })}
              </tbody>
            </table>
          </div>
          <div className="order-detail">
            {selectedOrder ? <>
              <div className="detail-label">SELECTED ORDER</div><div className="detail-title">#{selectedOrder.id}</div>
              <div className="detail-status"><span className={`order-status ${statusClass(selectedOrder.status)}`}>{selectedOrder.status.replaceAll("_", " ")}</span></div>
              <div className="detail-grid"><div><span>Pickup</span><b>#{selectedOrder.pickupNode}</b></div><div><span>Drop-off</span><b>#{selectedOrder.dropoffNode}</b></div><div><span>Driver</span><b>{selectedOrder.assignedDriverId ? `#${selectedOrder.assignedDriverId}` : "Unassigned"}</b></div><div><span>Route nodes</span><b>{selectedOrder.route?.length ?? 0}</b></div></div>
              <div className="detail-note">The map is centered on this order and renders the routing engine’s node sequence over Bengaluru geography.</div>
            </> : <div className="empty detail-empty">Select an order to inspect its route.</div>}
          </div>
        </div>
      </section>

      <footer className="footer-row"><div><span className="dot-row"><span className="small-dot green" />Backend {backendUp ? "healthy" : "offline"}</span><span className="dot-row"><span className={`small-dot ${connected ? "green" : "red"}`} />Telemetry {connected ? "connected" : "disconnected"}</span></div><div>Spring Boot :8080 · Dashboard :3000/:3001 · Bengaluru road graph</div></footer>
    </main>
  );
}
