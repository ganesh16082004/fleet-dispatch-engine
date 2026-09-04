package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import com.ganesh.fleetdispatch.persistence.EventOutboxDocument;
import com.ganesh.fleetdispatch.persistence.EventOutboxRepository;
import com.ganesh.fleetdispatch.persistence.OrderDocument;
import com.ganesh.fleetdispatch.persistence.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {
    private final DriverRepository driverRepository;
    private final OrderRepository orderRepository;
    private final EventOutboxRepository outboxRepository;

    public DashboardService(
            DriverRepository driverRepository,
            OrderRepository orderRepository,
            EventOutboxRepository outboxRepository) {
        this.driverRepository = driverRepository;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    public DashboardSummary summary() {
        List<DriverDocument> drivers = driverRepository.findAll();
        List<OrderDocument> orders = orderRepository.findAll();
        List<EventOutboxDocument> events = outboxRepository.findAll();

        long availableDrivers = drivers.stream().filter(d -> "AVAILABLE".equals(d.status())).count();
        long busyDrivers = drivers.stream().filter(d -> "BUSY".equals(d.status())).count();
        long offlineDrivers = drivers.stream().filter(d -> "OFFLINE".equals(d.status())).count();

        long createdOrders = countOrders(orders, "CREATED");
        long offeredOrders = countOrders(orders, "OFFERED");
        long assignedOrders = countOrders(orders, "ASSIGNED");
        long pickedUpOrders = countOrders(orders, "PICKED_UP");
        long recoveryOrders = countOrders(orders, "RECOVERY_REQUIRED");
        long completedOrders = countOrders(orders, "COMPLETED");
        long cancelledOrders = countOrders(orders, "CANCELLED");
        long activeOrders = assignedOrders + pickedUpOrders + recoveryOrders;

        long pendingOutboxEvents = events.stream().filter(event -> event.publishedAt() == null).count();
        long failedOutboxEvents = events.stream().filter(event -> event.publishedAt() == null && event.attempts() > 0).count();

        return new DashboardSummary(
                drivers.size(),
                availableDrivers,
                busyDrivers,
                offlineDrivers,
                orders.size(),
                createdOrders,
                offeredOrders,
                assignedOrders,
                pickedUpOrders,
                recoveryOrders,
                completedOrders,
                cancelledOrders,
                activeOrders,
                pendingOutboxEvents,
                failedOutboxEvents);
    }

    private static long countOrders(List<OrderDocument> orders, String status) {
        return orders.stream().filter(order -> status.equals(order.status())).count();
    }
}
