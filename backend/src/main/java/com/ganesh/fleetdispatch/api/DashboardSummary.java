package com.ganesh.fleetdispatch.api;

public record DashboardSummary(
        long totalDrivers,
        long availableDrivers,
        long busyDrivers,
        long offlineDrivers,
        long totalOrders,
        long createdOrders,
        long offeredOrders,
        long assignedOrders,
        long pickedUpOrders,
        long recoveryOrders,
        long completedOrders,
        long cancelledOrders,
        long activeOrders,
        long pendingOutboxEvents,
        long failedOutboxEvents) {
}
