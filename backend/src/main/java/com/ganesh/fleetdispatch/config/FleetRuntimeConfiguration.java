package com.ganesh.fleetdispatch.config;

import com.ganesh.fleetdispatch.dispatch.DashboardWebSocketServer;
import com.ganesh.fleetdispatch.dispatch.DeliveryConstraints;
import com.ganesh.fleetdispatch.dispatch.DispatchEngine;
import com.ganesh.fleetdispatch.dispatch.DriverFailureDetector;
import com.ganesh.fleetdispatch.dispatch.DriverFailureRecoveryCoordinator;
import com.ganesh.fleetdispatch.dispatch.DriverHeartbeatStore;
import com.ganesh.fleetdispatch.dispatch.DriverLocationTracker;
import com.ganesh.fleetdispatch.dispatch.DriverLocationWebSocketServer;
import com.ganesh.fleetdispatch.dispatch.DriverRecoveryQueue;
import com.ganesh.fleetdispatch.dispatch.DriverRecoveryWorker;
import com.ganesh.fleetdispatch.dispatch.DriverRouteStore;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverHeartbeatStore;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverRecoveryQueue;
import com.ganesh.fleetdispatch.dispatch.InMemoryDriverRouteStore;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.dispatch.PickedUpOrderRecoveryService;
import com.ganesh.fleetdispatch.dispatch.RecoveryCandidateSelector;
import com.ganesh.fleetdispatch.events.FleetEventPublisher;
import com.ganesh.fleetdispatch.routing.Router;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.Optional;

@Configuration
public class FleetRuntimeConfiguration {

    @Bean
    public DriverHeartbeatStore driverHeartbeatStore() {
        return new InMemoryDriverHeartbeatStore();
    }

    @Bean
    public DriverRouteStore driverRouteStore() {
        return new InMemoryDriverRouteStore();
    }

    @Bean
    public DriverRecoveryQueue driverRecoveryQueue() {
        return new InMemoryDriverRecoveryQueue();
    }

    @Bean
    public DriverLocationTracker driverLocationTracker(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore driverHeartbeatStore) {
        return new DriverLocationTracker(driverStateStore, driverHeartbeatStore);
    }

    @Bean
    public PickedUpOrderRecoveryService pickedUpOrderRecoveryService(
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRouteStore driverRouteStore,
            DriverRecoveryQueue driverRecoveryQueue) {
        return new PickedUpOrderRecoveryService(
                driverStateStore,
                orderStateStore,
                driverRouteStore,
                driverRecoveryQueue);
    }

    @Bean
    public DriverFailureRecoveryCoordinator driverFailureRecoveryCoordinator(
            DriverStateStore driverStateStore,
            PickedUpOrderRecoveryService pickedUpOrderRecoveryService,
            DispatchEngine dispatchEngine,
            FleetEventPublisher eventPublisher) {
        return new DriverFailureRecoveryCoordinator(
                driverStateStore,
                pickedUpOrderRecoveryService,
                dispatchEngine,
                new DeliveryConstraints(300.0, 1_800.0),
                eventPublisher);
    }

    @Bean
    public DriverFailureDetector driverFailureDetector(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore driverHeartbeatStore,
            DriverFailureRecoveryCoordinator recoveryCoordinator) {
        return new DriverFailureDetector(
                driverStateStore,
                driverHeartbeatStore,
                recoveryCoordinator,
                longProperty("FLEET_HEARTBEAT_TIMEOUT_MILLIS", 10_000L));
    }

    @Bean
    public RecoveryCandidateSelector recoveryCandidateSelector(
            DriverStateStore driverStateStore,
            Router router) {
        return new RecoveryCandidateSelector(
                driverStateStore,
                (source, target) -> {
                    try {
                        return Optional.of(router.findRoute(source, target));
                    } catch (IllegalArgumentException exception) {
                        return Optional.empty();
                    }
                });
    }

    @Bean
    public DriverRecoveryWorker driverRecoveryWorker(
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRouteStore driverRouteStore,
            DriverRecoveryQueue driverRecoveryQueue,
            RecoveryCandidateSelector recoveryCandidateSelector,
            Router router,
            FleetEventPublisher eventPublisher) {
        return new DriverRecoveryWorker(
                driverStateStore,
                orderStateStore,
                driverRouteStore,
                driverRecoveryQueue,
                recoveryCandidateSelector,
                (source, target) -> {
                    try {
                        return Optional.of(router.findRoute(source, target));
                    } catch (IllegalArgumentException exception) {
                        return Optional.empty();
                    }
                },
                eventPublisher);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DriverLocationWebSocketServer driverLocationWebSocketServer(
            DriverLocationTracker driverLocationTracker) {
        return new DriverLocationWebSocketServer(
                new InetSocketAddress("127.0.0.1", 8087),
                driverLocationTracker);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DashboardWebSocketServer dashboardWebSocketServer(
            DriverStateStore driverStateStore,
            DriverHeartbeatStore driverHeartbeatStore,
            DriverLocationTracker driverLocationTracker) {
        DashboardWebSocketServer server = new DashboardWebSocketServer(
                new InetSocketAddress("127.0.0.1", 8088),
                driverStateStore,
                driverHeartbeatStore);
        driverLocationTracker.addListener(server);
        return server;
    }

    private static long longProperty(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a valid long", exception);
        }
    }
}
