# Fleet Dispatch Engine

A real-time, event-driven fleet dispatch and optimization system running on a realistic Bengaluru road network simulation.

## Goal

Build a production-style dispatch engine that continuously assigns delivery orders to riders while balancing:

- Delivery time
- Rider travel distance
- Rider utilization
- Restaurant preparation time
- Delivery deadlines
- Operational cost
- Dynamic order batching

The project is designed as a systems and algorithms project, with reproducible simulations, correctness checks, benchmarks, and failure testing.

## Planned Architecture

```text
Bengaluru Road Network
        |
        v
City / Order / Rider Simulator
        |
        v
Custom Event System -----> MongoDB
        |                    ^
        v                    |
Concurrent Workers <----> Redis
        |
        v
Dispatch Engine
        |
        v
Optimization Engine
        |
        v
WebSocket API
        |
        v
Next.js Dashboard
```

Kafka will be introduced later as a second event-transport implementation after the custom event system has been benchmarked and its limitations are understood.

## Initial Technology Choices

- Core engine: Java
- Backend API: Spring Boot
- Persistent database: MongoDB
- Fast-changing state/cache: Redis
- Event transport: custom Java implementation first, Kafka later
- Frontend: Next.js + TypeScript
- Real-time updates: WebSockets
- Routing: graph-based A* / Dijkstra
- Testing: JUnit and property-based tests

## Project Principles

1. Build the core algorithm before introducing infrastructure complexity.
2. Keep a simple baseline implementation for comparison.
3. Make simulation runs deterministic with configurable random seeds.
4. Treat correctness and benchmarking as first-class features.
5. Record architectural trade-offs and performance results.
6. Implement the core logic ourselves; use external solvers only as validation or baselines.

## Roadmap

1. Project skeleton and domain model
2. Bengaluru road-network ingestion
3. Graph representation and routing
4. Deterministic city simulator
5. Order and rider lifecycle
6. Nearest-rider baseline dispatcher
7. Optimization engine
8. Dynamic batching
9. Custom event system
10. Concurrent workers and state management
11. Redis + MongoDB integration
12. Failure recovery and idempotency
13. Correctness and property-based testing
14. Load testing and benchmarks
15. Kafka transport implementation
16. Real-time dashboard
17. Documentation and deployment preparation

## Status

Early development — architecture and core domain are being defined before implementation.
