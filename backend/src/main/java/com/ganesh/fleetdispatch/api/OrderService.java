package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.dispatch.DispatchAssignment;
import com.ganesh.fleetdispatch.dispatch.DispatchEngine;
import com.ganesh.fleetdispatch.dispatch.Order;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.dispatch.OrderStatus;
import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.persistence.OrderDocument;
import com.ganesh.fleetdispatch.persistence.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderStateStore orderStateStore;
    private final DispatchEngine dispatchEngine;

    public OrderService(
            OrderRepository orderRepository,
            OrderStateStore orderStateStore,
            DispatchEngine dispatchEngine) {
        this.orderRepository = orderRepository;
        this.orderStateStore = orderStateStore;
        this.dispatchEngine = dispatchEngine;
    }

    public OrderResponse create(OrderRequest request) {
        validate(request);
        if (orderStateStore.getOrder(request.id()).isPresent()) {
            throw new IllegalArgumentException("Order already exists: " + request.id());
        }

        Order order = new Order(
                request.id(),
                new NodeId(request.pickupNode()),
                new NodeId(request.dropoffNode()),
                System.currentTimeMillis(),
                OrderStatus.CREATED);

        orderStateStore.addOrder(order);
        save(order);
        return response(order, null, List.of());
    }

    public List<OrderDocument> findAll() {
        return orderRepository.findAll();
    }

    public OrderDocument findById(long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    public OrderResponse dispatch(long id) {
        Order order = currentOrder(id);
        DispatchAssignment assignment = dispatchEngine.dispatch(order)
                .orElseThrow(() -> new IllegalStateException("No feasible driver found for order: " + id));
        Order updated = currentOrder(id);
        Long driverId = assignment.driverId();
        save(updated, driverId);
        return response(updated, driverId, assignment.route().nodes().stream().map(NodeId::value).toList());
    }

    public OrderResponse cancel(long id) {
        if (!dispatchEngine.cancelOrder(id)) {
            throw new IllegalStateException("Order cannot be cancelled: " + id);
        }
        Order updated = currentOrder(id);
        save(updated, null);
        return response(updated, null, List.of());
    }

    private Order currentOrder(long id) {
        return orderStateStore.getOrder(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    private void save(Order order) {
        save(order, null);
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
