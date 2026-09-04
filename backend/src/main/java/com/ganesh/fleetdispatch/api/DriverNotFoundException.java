package com.ganesh.fleetdispatch.api;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(long id) {
        super("Driver not found: " + id);
    }
}
