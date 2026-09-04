"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

type Driver = { id: number; currentNode: number; status: string; sequenceNumber?: number; lastHeartbeatMillis?: number };
type Summary = {
  totalDrivers: number; availableDrivers: number; busyDrivers: number; offlineDrivers: number;
  totalOrders: number; createdOrders: number; offeredOrders: number; assignedOrders: number;
  pickedUpOrders: number; recoveryOrders: number; completedOrders: number; cancelledOrders: number;
  activeOrders: number; pendingOutboxEvents: number; failedOutboxEvents: number;
};
type EventItem = {
  eventId: string; eventType: string; aggregateId: string; aggregateType: string;
  createdAt: string; publishedAt?: string | null; attempts: number; status: string; payload?: Record<string, unknown>;
};
type LiveLocation = { driverId: number; nodeId: number; sequenceNumber: number; timestampMillis: number };

type DriverMap = Record<number, Driver>;
type LocationMap = Record<number, LiveLocation>;

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const DASHBOARD_WS = process.env.NEXT_PUBLIC_DASHBOARD_WS_URL ?? "ws://127.0.0.1:8088/dashboard";

const initialSummary: Summary = {
  totalDrivers: 0, availableDrivers: 0, busyDrivers: 0, offlineDrivers: 0,
  totalOrders: 0, createdOrders: 0, offeredOrders: 0, assignedOrders: 0,
  pickedUpOrders: 0, recoveryOrders: 0, completedOrders: 0, cancelledOrders: 0,
  activeOrders: 0, pendingOutboxEvents: 0, failedOutboxEvents: 0
};

function timeLabel(value?: string | number | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function humanEvent(event: EventItem) {
  const p = event.payload ?? {};
  if (event.aggregateType === "DRIVER") {
    return `Driver #${event.aggregateId} · ${String(p.status ?? event.eventType).replaceAll("_", " ")}`;
  }
  if (event.aggregateType === "ORDER") {
    return `Order #${event.aggregateId} · ${String(p.status ?? event.eventType).replaceAll("_", " ")}`;
  }
  return `${event.aggregateType} · ${event.aggregateId}`;
}

function nodePosition(node: number, sequence = 0) {
  const x = 10 + (Math.abs(node * 37 + sequence * 11) % 800) / 10;
  const y = 11 + (Math.abs(node * 19 + sequence * 17) % 760) / 10;
  return { x: Math.min(92, x), y: Math.min(91, y) };
}

export default function Home() {
  const [summary, setSummary] = useState<Summary>(initialSummary);
  const [drivers, setDrivers] = useState<DriverMap>({});
  const [locations, setLocations] = useState<LocationMap>({});
  const [events, setEvents] = useState<EventItem[]>([]);
  const [connected, setConnected] = useState(false);
  const [lastRefresh, setLastRefresh] = useState<number | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  const load = useCallback(async () => {
    try {
      const [summaryResponse, driversResponse, eventsResponse] = await Promise.all([
        fetch(`${API_BASE}/api/v1/dashboard/summary`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/drivers`, { cache: "no-store" }),
        fetch(`${API_BASE}/api/v1/events/recent?limit=35`, { cache: "no-store" })
      ]);
      if (summaryResponse.ok) setSummary(await summaryResponse.json());
      if (driversResponse.ok) {
        const body = await driversResponse.json();
        const next: DriverMap = {};
        for (const d of body as Driver[]) next[d.id] = d;
        setDrivers(next);
      }
      if (eventsResponse.ok) setEvents(await eventsResponse.json());
      setLastRefresh(Date.now());
    } catch {
      setConnected(false);
    }
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, 5000);
    return () => window.clearInterval(timer);
  }, [load]);

  useEffect(() => {
    let socket: WebSocket | null = null;
    let retry: number | null = null;
    let stopped = false;

    const connect = () => {
      if (stopped) return;
      try {
        socket = new WebSocket(DASHBOARD_WS);
        wsRef.current = socket;
        socket.onopen = () => setConnected(true);
        socket.onclose = () => {
          setConnected(false);
          if (!stopped) retry = window.setTimeout(connect, 2500);
        };
        socket.onerror = () => setConnected(false);
        socket.onmessage = (message) => {
          try {
            const m = JSON.parse(message.data as string) as { type?: string; drivers?: Driver[] } & LiveLocation;
            if (m.type === "snapshot" && Array.isArray(m.drivers)) {
              setDrivers((current) => {
                const next = { ...current };
                for (const d of m.drivers!) next[d.id] = { ...next[d.id], ...d };
                return next;
              });
              return;
            }
            if (m.type === "location" || typeof m.driverId === "number") {
              const live: LiveLocation = {
                driverId: Number(m.driverId), nodeId: Number(m.nodeId),
                sequenceNumber: Number(m.sequenceNumber ?? 0), timestampMillis: Number(m.timestampMillis ?? Date.now())
              };
              setLocations((current) => {
                const previous = current[live.driverId];
                if (previous && live.sequenceNumber < previous.sequenceNumber) return current;
                return { ...current, [live.driverId]: live };
              });
              setDrivers((current) => ({
                ...current,
                [live.driverId]: {
                  ...(current[live.driverId] ?? { id: live.driverId, currentNode: live.nodeId, status: "AVAILABLE" }),
                  currentNode: live.nodeId,
                  sequenceNumber: live.sequenceNumber,
                  lastHeartbeatMillis: live.timestampMillis
                }
              }));
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
      wsRef.current = null;
    };
  }, []);

  const driverList = useMemo(() => Object.values(drivers).sort((a, b) => a.id - b.id), [drivers]);
  const activeDrivers = summary.totalDrivers ? summary.totalDrivers - summary.offlineDrivers : driverList.filter((d) => d.status !== "OFFLINE").length;
  const fleetUtilization = summary.totalDrivers ? Math.round((summary.busyDrivers / summary.totalDrivers) * 100) : 0;

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand">
          <div className="logo"><div className="logo-mark" /></div>
          <div>
            <h1>Fleet Dispatch Command Center</h1>
            <div className="subtitle">Real-time routing · intelligent dispatch · recovery orchestration</div>
          </div>
        </div>
        <div className={`live ${connected ? "" : "off"}`}>
          <span className="live-dot" /> {connected ? "LIVE TELEMETRY" : "API ONLY"}
        </div>
      </header>

      <section className="kpis">
        <div className="card metric"><div className="metric-label">Fleet health</div><div className="metric-row"><div className="metric-value">{activeDrivers}</div><div className="metric-foot">/ {summary.totalDrivers} active</div></div></div>
        <div className="card metric"><div className="metric-label">Orders in motion</div><div className="metric-row"><div className="metric-value">{summary.activeOrders}</div><div className="metric-foot">{summary.completedOrders} completed</div></div></div>
        <div className="card metric"><div className="metric-label">Utilization</div><div className="metric-row"><div className="metric-value">{fleetUtilization}%</div><div className="metric-foot">{summary.busyDrivers} busy</div></div></div>
        <div className="card metric"><div className="metric-label">Recovery queue</div><div className="metric-row"><div className="metric-value">{summary.recoveryOrders}</div><div className="metric-foot">{summary.failedOutboxEvents} failed events</div></div></div>
      </section>

      <section className="main-grid">
        <article className="card map-card">
          <div className="card-head"><div><h2 className="card-title">Live fleet topology</h2><div className="card-hint">WebSocket telemetry · node movement · heartbeat state</div></div><div className="card-hint">Updated {timeLabel(lastRefresh)}</div></div>
          <div className="map">
            <div className="gridline" />
            <div className="road r1" /><div className="road r2" /><div className="road r3" /><div className="road r4" />
            {Array.from({ length: 26 }, (_, i) => <span key={i} className="node" style={{ left: `${7 + ((i * 17) % 86)}%`, top: `${9 + ((i * 29) % 80)}%` }} />)}
            {driverList.map((driver) => {
              const live = locations[driver.id];
              const point = nodePosition(live?.nodeId ?? driver.currentNode, live?.sequenceNumber ?? driver.sequenceNumber ?? 0);
              const isOffline = driver.status === "OFFLINE";
              return <div key={driver.id} className="driver-dot" title={`Driver #${driver.id} · ${driver.status}`} style={{ left: `${point.x}%`, top: `${point.y}%`, opacity: isOffline ? .3 : 1, background: isOffline ? "#7d8b85" : driver.status === "BUSY" ? "#f2c96b" : "#62e6a7" }}><span>#{driver.id}</span></div>;
            })}
            <div className="map-legend"><span className="legend-pill"><b>{summary.availableDrivers}</b> available</span><span className="legend-pill"><b>{summary.busyDrivers}</b> busy</span><span className="legend-pill"><b>{summary.offlineDrivers}</b> offline</span><span className="legend-pill"><b>{driverList.length}</b> tracked</span></div>
          </div>
        </article>

        <aside className="side">
          <article className="card panel">
            <div className="card-head"><div><h2 className="card-title">Driver operations</h2><div className="card-hint">Current assignment state</div></div></div>
            <div className="scroll">
              <table className="table"><thead><tr><th>DRIVER</th><th>STATE</th><th>NODE</th><th>HEARTBEAT</th></tr></thead><tbody>
                {driverList.map((driver) => <tr key={driver.id}><td>#{driver.id}</td><td><span className={`status ${driver.status.toLowerCase()}`}>{driver.status}</span></td><td>{locations[driver.id]?.nodeId ?? driver.currentNode}</td><td>{timeLabel(locations[driver.id]?.timestampMillis ?? driver.lastHeartbeatMillis)}</td></tr>)}
                {!driverList.length && <tr><td colSpan={4}>No drivers registered yet.</td></tr>}
              </tbody></table>
            </div>
          </article>

          <article className="card panel">
            <div className="card-head"><div><h2 className="card-title">Event stream</h2><div className="card-hint">Kafka-backed operational history</div></div><div className="card-hint">{events.length} recent</div></div>
            <div className="events">
              {events.map((event) => <div className="event" key={event.eventId}><div className="event-time">{timeLabel(event.createdAt)}</div><div className="event-main"><div className="event-type">{event.eventType.replaceAll("_", " ")}</div><div className="event-copy">{humanEvent(event)}</div></div></div>)}
              {!events.length && <div className="event-copy" style={{ padding: "12px 0" }}>Waiting for events…</div>}
            </div>
          </article>
        </aside>
      </section>

      <div className="footer-row"><span className="dot-row"><span className="small-dot" style={{ background: connected ? "var(--green)" : "var(--yellow)" }} /> Backend {connected ? "stream connected" : "reachable via REST polling"}</span><span>Outbox: {summary.pendingOutboxEvents} pending · {summary.failedOutboxEvents} failed · {summary.totalOrders} total orders</span></div>
    </main>
  );
}
