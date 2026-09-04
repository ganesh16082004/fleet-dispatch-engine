package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.dispatch.DispatchAssignment;
import com.ganesh.fleetdispatch.dispatch.DispatchEngine;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.DriverStatus;
import com.ganesh.fleetdispatch.dispatch.Order;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.dispatch.OrderStatus;
import com.ganesh.fleetdispatch.events.FleetEventPublisher;
import com.ganesh.fleetdispatch.events.FleetEventType;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import com.ganesh.fleetdispatch.persistence.OrderDocument;
import com.ganesh.fleetdispatch.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderStateStore orderStateStore;
    private final DispatchEngine dispatchEngine;
    private final DriverRepository driverRepository;
    private final DriverStateStore driverStateStore;
    private final FleetEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            OrderStateStore orderStateStore,
            DispatchEngine dispatchEngine,
            DriverRepository driverRepository,
            DriverStateStore driverStateStore,
            FleetEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStateStore = orderStateStore;
        this.dispatchEngine = dispatchEngine;
        this.driverRepository = driverRepository;
        this.driverStateStore = driverStateStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        validate(request);
        if (orderStateStore.getOrder(request.id()).isPresent() || orderRepository.existsById(request.id())) {
            throw new OrderConflictException("Order already exists: " + request.id());
        }

        Order order = new Order(
                request.id(),
                new NodeId(request.pickupNode()),
                new NodeId(request.dropoffNode()),
                System.currentTimeMillis(),
                OrderStatus.CREATED);

        orderStateStore.addOrder(order);
        save(order, null);
        eventPublisher.publish(
                FleetEventType.ORDER_CREATED,
                "order-" + order.id(),
                "ORDER",
                Map.of(
                        "orderId", order.id(),
                        "pickupNode", order.pickupNode().value(),
                        "dropoffNode", order.dropoffNode().value()));
        return response(order, null, List.of());
    }

    public List<OrderDocument> findAll() {
        return orderRepository.findAll();
    }

    public OrderDocument findById(long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public OrderResponse dispatch(long id) {
        Order order = currentOrder(id);
        DispatchAssignment assignment = dispatchEngine.dispatch(order)
                .orElseThrow(() -> new IllegalStateException("No feasible driver found for order: " + id));

        Order updated = currentOrder(id);
        long driverId = assignment.driverId();
        persistDriverStatus(driverId, DriverStatus.BUSY);
        save(updated, driverId);
        eventPublisher.publish(
                FleetEventType.ORDER_ASSIGNED,
                "order-" + id,
                "ORDER",
                Map.of("orderId", id, "driverId", driverId));

        return response(updated, driverId,
                assignment.driverToPickupRoute().nodes().stream().map(NodeId::value).toList());
    }

    @Transactional
    public OrderResponse pickup(long id) {
        Order order = currentOrder(id);
        long driverId = assignedDriverId(id, order);

        if (!orderStateStore.tryTransition(id, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)) {
            throw new IllegalStateException("Order is not ASSIGNED: " + id);
        }

        Order updated = currentOrder(id);
        save(updated, driverId);
        eventPublisher.publish(
                FleetEventType.ORDER_PICKED_UP,
                "order-" + id,
                "ORDER",
                Map.of("orderId", id, "driverId", driverId));
        return response(updated, driverId, List.of());
    }

    @Transactional
    public OrderResponse complete(long id) {
        Order order = currentOrder(id);
        long driverId = assignedDriverId(id, order);

        if (!orderStateStore.tryTransition(id, OrderStatus.PICKED_UP, OrderStatus.COMPLETED)) {
            throw new IllegalStateException("Order is not PICKED_UP: " + id);
        }

        persistDriverStatus(driverId, DriverStatus.AVAILABLE);
        Order updated = currentOrder(id);
        save(updated, driverId);
        eventPublisher.publish(
                FleetEventType.ORDER_COMPLETED,
                "order-" + id,
                "ORDER",
                Map.of("orderId", id, "driverId", driverId));
        return response(updated, driverId, List.of());
    }

    @Transactional
    public OrderResponse cancel(long id) {
        Long assignedDriverId = orderStateStore.getAssignedDriverId(id).isPresent()
                ? orderStateStore.getAssignedDriverId(id).getAsLong()
                : null;

        if (!dispatchEngine.cancelOrder(id)) {
            throw new IllegalStateException("Order cannot be cancelled: " + id);
        }
        Order updated = currentOrder(id);
        if (assignedDriverId != null) {
            persistDriverStatus(assignedDriverId, DriverStatus.AVAILABLE);
        }
        save(updated, null);
        if (assignedDriverId != null) {
            eventPublisher.publish(
                    FleetEventType.ORDER_CANCELLED,
                    "order-" + id,
                    "ORDER",
                    Map.of("orderId", id, "driverId", assignedDriverId));
        } else {
            eventPublisher.publish(
                    FleetEventType.ORDER_CANCELLED,
                    "order-" + id,
                    "ORDER",
                    Map.of("orderId", id));
        }
        return response(updated, null, List.of());
    }

    private long assignedDriverId(long id, Order order) {
        if (order.status() != OrderStatus.ASSIGNED && order.status() != OrderStatus.PICKED_UP) {
            throw new IllegalStateException("Order has no active driver: " + id);
        }
        return orderStateStore.getAssignedDriverId(id)
                .orElseThrow(() -> new IllegalStateException("Order has no assigned driver: " + id));
    }

    private void persistDriverStatus(long driverId, DriverStatus status) {
        DriverDocument existing = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalStateException("Assigned driver not found: " + driverId));
        driverRepository.save(new DriverDocument(existing.id(), existing.currentNode(), status.name()));
        driverStateStore.updateStatus(driverId, status);
    }

    private Order currentOrder(long id) {
        return orderStateStore.getOrder(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void save(Order order, Long assignedDriverId) {
        orderRepository.save(OrderDocument.from(order, assignedDriverId));
    }

    private static OrderResponse response(Order order, Long driverId, List<Long> route) {
        return new OrderResponse(
                order.id(),
                order.pickupNode().value(),
                order.dropoffNode().value(),
                order.requestTimestamp(),
                order.status().name(),
                driverId,
                route);
    }

    private static void validate(OrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.id() < 0 || request.pickupNode() < 0 || request.dropoffNode() < 0) {
            throw new IllegalArgumentException("id, pickupNode and dropoffNode must be non-negative");
        }
    }
}
