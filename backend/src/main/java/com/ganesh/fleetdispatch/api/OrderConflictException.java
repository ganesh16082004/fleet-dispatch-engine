package com.ganesh.fleetdispatch.api;

public class OrderConflictException extends RuntimeException {
    public OrderConflictException(String message) {
        super(message);
    }
}
