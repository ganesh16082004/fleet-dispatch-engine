package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.Route;
import java.util.Objects;

/** Deterministic route-based ETA calculator used by V3. */
public final class EtaEngine {
    private final double defaultSpeedMetersPerSecond;
    private final double pickupDwellSeconds;
    private final double dropoffDwellSeconds;

    public EtaEngine(double defaultSpeedMetersPerSecond, double pickupDwellSeconds, double dropoffDwellSeconds) {
        if (!Double.isFinite(defaultSpeedMetersPerSecond) || defaultSpeedMetersPerSecond <= 0
                || !Double.isFinite(pickupDwellSeconds) || pickupDwellSeconds < 0
                || !Double.isFinite(dropoffDwellSeconds) || dropoffDwellSeconds < 0) {
            throw new IllegalArgumentException("invalid ETA configuration");
        }
        this.defaultSpeedMetersPerSecond = defaultSpeedMetersPerSecond;
        this.pickupDwellSeconds = pickupDwellSeconds;
        this.dropoffDwellSeconds = dropoffDwellSeconds;
    }

    public EtaEngine() {
        this(10.0, 30.0, 20.0);
    }

    public double estimateSeconds(Route route) {
        Objects.requireNonNull(route, "route");
        return estimateSeconds(route, defaultSpeedMetersPerSecond);
    }

    public double estimateSeconds(Route route, double speedMetersPerSecond) {
        Objects.requireNonNull(route, "route");
        if (!Double.isFinite(speedMetersPerSecond) || speedMetersPerSecond <= 0) {
            throw new IllegalArgumentException("speedMetersPerSecond must be positive and finite");
        }
        return route.totalDistanceMeters() / speedMetersPerSecond + route.totalTravelTimeSeconds() / 2.0;
    }

    public double estimateStopSeconds(Route route, RouteStopType stopType) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(stopType, "stopType");
        double travel = estimateSeconds(route);
        return travel + switch (stopType) {
            case PICKUP, HANDOFF -> pickupDwellSeconds;
            case DROPOFF -> dropoffDwellSeconds;
        };
    }

    public static EtaEngine fromAverageSpeedKmh(double speedKmh) {
        if (!Double.isFinite(speedKmh) || speedKmh <= 0) {
            throw new IllegalArgumentException("speedKmh must be positive and finite");
        }
        return new EtaEngine(speedKmh / 3.6, 30.0, 20.0);
    }
}
