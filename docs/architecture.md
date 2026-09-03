# System Architecture

## 1. High-level design

The Fleet Dispatch Engine is organized around a continuously changing simulation state. Orders, rider locations, restaurant preparation events, cancellations, and other changes enter the system as events. Dispatch decisions are derived from the latest valid state and can be recomputed as conditions change.

```text
                    +----------------------+
                    | Bengaluru Road Graph |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |    City Simulator     |
                    | orders / riders /     |
                    | traffic / restaurants |
                    +----------+-----------+
                               |
                               v
                    +----------------------+
                    |   Event Dispatcher    |
                    +----------+-----------+
                               |
                    +----------+-----------+
                    |                      |
                    v                      v
             +-------------+       +-------------+
             | Event Queue |       |  State Store |
             | custom v1   |       | Redis/Mongo  |
             +------+------+       +-------------+
                    |
                    v
             +-------------+
             | Worker Pool |
             +------+------+ 
                    |
                    v
             +-------------------+
             | Dispatch Engine   |
             +---------+---------+
                       |
                       v
             +-------------------+
             | Optimization Engine|
             +---------+---------+
                       |
                       v
             +-------------------+
             | Assignment / Route|
             +---------+---------+
                       |
                       v
             +-------------------+
             | WebSocket / API   |
             +---------+---------+
                       |
                       v
             +-------------------+
             | Next.js Dashboard |
             +-------------------+
```

## 2. Core domain

### Rider

Represents a delivery agent and contains current position, availability, vehicle capacity, active assignments, and operational characteristics.

### Restaurant

Represents an order pickup location and its preparation behavior.

### Customer

Represents a delivery destination.

### Order

Represents a delivery request and its lifecycle from creation through delivery or cancellation.

### Road graph

Represents the physical network. Nodes are intersections/locations and edges represent traversable road segments with distance and travel-time properties.

## 3. State model

Persistent business records will eventually live in MongoDB. High-frequency operational state such as current rider positions and active dispatch state will use Redis once the basic engine is stable.

The system should avoid treating the database as the source of truth for every GPS update. High-frequency state changes should be handled through an appropriate in-memory/fast state layer and periodically persisted where necessary.

## 4. Event model

The first implementation will use a custom Java event system. Events should have:

- Unique event ID
- Event type
- Aggregate/entity ID
- Simulation timestamp
- Creation timestamp
- Payload
- Version where needed

The event-processing layer must eventually support idempotency, retries, ordering assumptions, and backpressure.

Kafka will be added later as an alternative transport after the custom implementation is benchmarked.

## 5. Dispatch lifecycle

```text
Order created
    -> identify feasible riders
    -> calculate route/travel estimates
    -> score candidate assignments
    -> select assignment
    -> persist decision
    -> publish assignment event
    -> rider executes route
    -> state changes trigger re-evaluation when required
```

## 6. Optimization objectives

The initial objective is a weighted cost function:

```text
cost =
    alpha * delivery_time
  + beta  * rider_distance
  + gamma * rider_idle_time
  + delta * late_delivery_penalty
  + epsilon * operating_cost
```

Later versions may add order contribution margin and batching effects.

## 7. Correctness strategy

The project will maintain a simple baseline and a validator. We will test invariants such as:

- An order cannot have conflicting active assignments.
- A rider cannot exceed capacity.
- Invalid state transitions are rejected.
- A route must contain valid graph edges.
- Duplicate events must not create duplicate business effects.
- Feasible assignments must respect hard constraints.

For small scenarios, an exact optimization solution can be used to measure the optimality gap of our heuristics.

## 8. Performance strategy

Every major version will be benchmarked using deterministic simulation seeds. We will measure throughput, queue latency, dispatch latency, CPU/memory usage, and optimization quality. Larger scenarios will be used to expose scalability bottlenecks before introducing Kafka.
