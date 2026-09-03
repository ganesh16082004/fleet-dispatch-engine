# Fleet Dispatch Engine — V1 Specification

**Status:** Draft for implementation lock  
**Version:** 1.0  
**Primary goal:** Build a reproducible, real-time, event-driven fleet dispatch system over a real Bengaluru road network simulation.

---

## 1. Problem Statement

Delivery fleets make continuously changing assignment decisions while orders, rider locations, restaurant readiness, traffic, and vehicle availability change over time.

The V1 system will simulate this environment and provide a dispatch engine that assigns orders to riders while optimizing delivery performance and operational cost under explicit constraints.

The system is a **simulation and systems-engineering project**. It does not use real customer, rider, Swiggy, Zomato, or Zepto operational data.

---

## 2. V1 Objectives

V1 must demonstrate all of the following:

1. A realistic road graph derived from a bounded Bengaluru region.
2. Deterministic simulation of riders, restaurants, customers, orders, and traffic.
3. Real-time event generation and processing.
4. A correct baseline dispatch algorithm.
5. A more advanced dispatch/optimization strategy that can be objectively compared with the baseline.
6. Dynamic order batching subject to capacity and SLA constraints.
7. Concurrent event processing with multiple workers.
8. Thread-safe state management.
9. Persistent state in MongoDB and low-latency operational state in Redis.
10. Automated correctness validation.
11. Reproducible performance benchmarks.
12. A real-time dashboard showing fleet and system state.
13. A second event transport implementation using Kafka after the custom event system is validated.

---

## 3. Non-Goals for V1

The following are explicitly outside V1 unless required later:

- Production deployment infrastructure.
- Containerization/Docker.
- Real delivery-platform APIs.
- Real rider/customer personal data.
- Full-city Bengaluru coverage from day one.
- Perfect real-time traffic prediction.
- Deep reinforcement learning.
- Mobile applications.
- Payment processing.
- Authentication/authorization beyond what is required for a local demo.

---

## 4. Technology Stack

### Core
- Java 21+
- Spring Boot

### Data
- MongoDB for durable application and historical state
- Redis for frequently accessed operational state and caching

### Event transport
- V1-A: custom Java event queue/worker system
- V1-B: Kafka implementation using the same event contracts

### Frontend
- Next.js
- TypeScript
- WebSocket-based live updates

### Algorithms
- Graph-based routing with Dijkstra and A*
- Greedy dispatch baseline
- Dynamic batching
- Custom adaptive dispatch strategy
- Optional external optimization solver only as a validation/baseline tool

### Testing
- JUnit
- Property-based/randomized scenario testing
- Load and benchmark harness

---

## 5. Domain Model

### Rider

Fields:
- id
- current location / graph node
- status: AVAILABLE, TO_PICKUP, LOADED, OFFLINE
- vehicle type
- capacity
- current orders
- speed profile
- operating cost per distance/time unit

### Restaurant

Fields:
- id
- location
- preparation-time distribution/configuration
- current kitchen load

### Customer

Fields:
- id
- delivery location
- optional delivery time window/deadline

### Order

Fields:
- id
- restaurant id
- customer id
- created time
- ready time
- assigned rider id
- status
- priority
- value/revenue
- deadline
- estimated cost
- estimated margin

### Road Node

Fields:
- id
- latitude
- longitude

### Road Edge

Fields:
- source node
- destination node
- distance
- base speed
- current traffic multiplier
- travel time
- road restrictions where applicable

---

## 6. Order Lifecycle

```text
CREATED
  |
  v
PREPARING
  |
  v
READY
  |
  v
ASSIGNED
  |
  v
PICKED_UP
  |
  v
DELIVERED
```

Cancellation may occur from any live state before delivery where the simulation allows it.

Every state transition must be validated. Invalid transitions must be rejected and observable in logs/tests.

---

## 7. Rider Lifecycle

```text
AVAILABLE
   |
   v
TO_PICKUP
   |
   v
LOADED
   |
   v
AVAILABLE
```

A rider can become OFFLINE or FAIL during the simulation. Recovery must return the rider to a valid state according to the failure scenario.

---

## 8. Bengaluru Simulation Environment

### 8.1 Road network

Use OpenStreetMap-derived road data for a bounded Bengaluru region. V1 will use a manageable subregion rather than the full metropolitan area.

The ingestion pipeline must convert the source data into an internal weighted graph.

### 8.2 Synthetic operational data

The simulator generates:

- restaurants
- customers
- riders
- orders
- preparation times
- traffic conditions
- rider movements

This data must be generated from a deterministic seed.

### 8.3 Simulation clock

The simulator uses an accelerated logical clock. Simulation time must be separated from wall-clock time so experiments are repeatable.

---

## 9. Routing Requirements

V1 must support:

### Dijkstra
- Correct shortest-path computation using travel-time edge weights.

### A*
- Correct shortest-path computation using an admissible geographic heuristic for the chosen edge-cost definition.

### Routing API

Given source and destination nodes, return:
- ordered path nodes
- total distance
- estimated travel time
- computation latency
- nodes explored (where available)

Routing failures must return explicit errors rather than invalid paths.

---

## 10. Dispatch Requirements

### Baseline strategy

**Nearest Available Rider**:
- filter eligible riders
- compute route/travel cost to pickup
- assign the lowest-cost eligible rider

### Advanced strategy

The advanced strategy must evaluate more than raw distance, including as applicable:

- travel time
- rider workload
- pickup wait time
- delivery deadline
- operational cost
- batching opportunities

The scoring function must be configurable and documented.

Example conceptual cost:

```text
score =
    w_time * predicted_delivery_time
  + w_distance * rider_distance
  + w_wait * restaurant_wait
  + w_cost * operational_cost
  + w_late * deadline_penalty
```

The exact formula and weights will be finalized after baseline experiments.

---

## 11. Dynamic Batching

The system may combine orders when all required constraints are satisfied.

V1 constraints include:

- rider capacity
- pickup compatibility
- maximum allowed detour
- delivery deadline/SLA
- route feasibility

Batching decisions must be explainable in logs/metrics: which orders were considered, why a batch was accepted/rejected, and expected impact.

---

## 12. Event System

### Event contract

Every event must contain at minimum:

- event id
- event type
- entity id
- simulation timestamp
- producer/source
- payload version

### Initial custom event system

Implement:

- thread-safe producer/consumer queue
- worker pool
- configurable worker count
- backpressure policy
- retry policy
- graceful shutdown
- idempotency guard
- metrics for queue depth and processing latency

### Kafka phase

Kafka must consume the same logical event contracts. The dispatch engine must not depend on Kafka-specific business logic.

---

## 13. Concurrency Requirements

Multiple workers may process unrelated events concurrently.

The system must prevent:

- double assignment of the same rider
- double processing of an order transition
- invalid concurrent state transitions
- capacity violations

Concurrency primitives must be chosen deliberately and documented.

---

## 14. State Management

### Redis

Used for hot operational state such as:

- rider availability
- active rider location
- active orders
- dispatch locks/claims
- cached route results where useful

### MongoDB

Used for durable state such as:

- orders
- riders
- restaurants
- completed deliveries
- dispatch decisions
- experiment metadata
- historical metrics

The exact collection schema may evolve during implementation but the separation of hot vs durable state is part of the V1 architecture.

---

## 15. Correctness Requirements

The system must automatically validate invariants including:

1. A rider cannot exceed vehicle capacity.
2. An order cannot be assigned to two riders at the same time.
3. Every active order has a valid lifecycle state.
4. Every rider has a valid lifecycle state.
5. A delivered order cannot return to a live state.
6. A route must contain valid adjacent graph edges.
7. A failed dispatch must not silently lose an order.
8. Duplicate events must not create duplicate state transitions.
9. Batches must satisfy all declared constraints.
10. The benchmark runner must be able to reproduce a scenario from its seed/configuration.

---

## 16. Validation Strategy

### Unit tests

Test graph operations, routing, cost calculations, state transitions, batching constraints, and event handling independently.

### Integration tests

Validate event → state → dispatch → persistence flows.

### Property/randomized tests

Generate many random scenarios and validate invariants automatically.

### Exact small-instance comparison

For small fleet/order sets, compare the custom optimizer against an exact/known optimization result where feasible and report optimality gap.

### Failure tests

Explicitly test:

- worker failure
- duplicate events
- out-of-order events
- temporary Redis failure
- persistence failure
- rider failure/offline transition

---

## 17. Benchmarking

All algorithm comparisons must use the same seed and scenario configuration.

Baseline metrics:

- average delivery time
- P95 delivery time
- total distance
- operational cost
- late-delivery rate
- rider utilization
- batching rate
- contribution margin where configured
- dispatch decision latency
- events processed per second
- queue lag
- CPU and memory usage

Experiments must record:

- scenario size
- seed
- algorithm/version
- configuration/weights
- environment information
- result metrics

No performance number may be presented in documentation unless it was measured by the benchmark harness.

---

## 18. Dashboard Requirements

V1 dashboard must display:

- Bengaluru simulated road network
- active riders and their states
- active orders
- routes/assignments
- restaurant and customer points
- batching relationships where useful
- live system metrics
- event throughput
- queue depth/lag
- average and P95 delivery metrics

The dashboard is a monitoring/visualization layer; business logic remains in the backend.

---

## 19. Failure and Recovery Model

The project must support controlled fault injection for at least:

- worker crash
- duplicate event
- delayed/out-of-order event
- rider going offline
- temporary cache outage

The recovery behavior must be deterministic enough to test and must preserve the correctness invariants.

---

## 20. Repository Structure (Target)

```text
fleet-dispatch-engine/
├── backend/
│   ├── api/
│   ├── dispatch/
│   ├── routing/
│   ├── state/
│   └── events/
├── simulator/
│   ├── city/
│   ├── traffic/
│   ├── riders/
│   ├── restaurants/
│   └── orders/
├── optimizer/
│   ├── baseline/
│   ├── batching/
│   └── strategies/
├── dashboard/
├── tests/
├── benchmarks/
├── docs/
└── README.md
```

Exact Maven/Gradle module boundaries may change once the implementation begins.

---

## 21. Definition of Done for V1

V1 is complete when all of the following are true:

- A Bengaluru road-network scenario can be loaded.
- A deterministic simulation can generate riders/orders/events.
- Dijkstra and A* produce valid routes.
- The nearest-rider baseline works.
- The advanced dispatcher works and is benchmarkable.
- Dynamic batching works and respects constraints.
- The custom event system processes concurrent events safely.
- MongoDB and Redis are integrated with clearly defined responsibilities.
- Core invariants are automatically validated.
- Failure scenarios are tested.
- A benchmark suite produces reproducible comparison results.
- A live dashboard visualizes the simulation.
- Kafka can be substituted for the custom event transport without changing dispatch business logic.
- The repository documents architecture, trade-offs, experiments, and measured results.

---

## 22. Engineering Rules

1. Do not optimize before establishing a measurable baseline.
2. Do not add infrastructure without a demonstrated reason.
3. Prefer deterministic simulations for benchmarking.
4. Separate domain logic from transport and persistence.
5. Keep algorithms replaceable through interfaces.
6. Test invariants, not only example outputs.
7. Benchmark every major optimization against a fixed baseline.
8. Record important design decisions in `docs/`.
9. Keep external solver usage limited to validation/baseline purposes where practical.
10. Every major subsystem should be independently explainable in an interview.
