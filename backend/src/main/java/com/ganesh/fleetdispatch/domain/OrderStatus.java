package com.ganesh.fleetdispatch.domain;

public enum OrderStatus {
    CREATED,
    PREPARING,
    READY,
    ASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED
}
