package com.ganesh.fleetdispatch.config;

import com.ganesh.fleetdispatch.dispatch.DashboardWebSocketHandler;
import com.ganesh.fleetdispatch.dispatch.DriverLocationMessageCodec;
import com.ganesh.fleetdispatch.dispatch.DriverLocationTracker;
import com.ganesh.fleetdispatch.dispatch.DriverLocationWebSocketHandler;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.DriverHeartbeatStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class FleetWebSocketConfiguration implements WebSocketConfigurer {
    private final DriverLocationWebSocketHandler driverLocationHandler;
    private final DashboardWebSocketHandler dashboardHandler;

    public FleetWebSocketConfiguration(
            DriverLocationWebSocketHandler driverLocationHandler,
            DashboardWebSocketHandler dashboardHandler) {
        this.driverLocationHandler = driverLocationHandler;
        this.dashboardHandler = dashboardHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dashboardHandler, "/ws/dashboard")
                .setAllowedOriginPatterns(allowedOriginPatterns());
        registry.addHandler(driverLocationHandler, "/ws/drivers/{driverId}")
                .setAllowedOriginPatterns(allowedOriginPatterns());
    }

    @Bean
    public DriverLocationMessageCodec driverLocationMessageCodec() {
        return new DriverLocationMessageCodec();
    }

    @Bean
    public DriverLocationWebSocketHandler driverLocationWebSocketHandler(
            DriverLocationTracker locationTracker,
            DriverLocationMessageCodec messageCodec) {
        return new DriverLocationWebSocketHandler(locationTracker, messageCodec);
    }

    @Bean
    public DashboardWebSocketHandler dashboardWebSocketHandler(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore driverHeartbeatStore,
            DriverLocationTracker driverLocationTracker) {
        DashboardWebSocketHandler handler = new DashboardWebSocketHandler(
                driverStateStore,
                driverHeartbeatStore);
        driverLocationTracker.addListener(handler);
        return handler;
    }

    private static String[] allowedOriginPatterns() {
        String raw = System.getenv("FLEET_WS_ALLOWED_ORIGINS");
        if (raw == null || raw.isBlank()) {
            return new String[] { "*" };
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }
}
