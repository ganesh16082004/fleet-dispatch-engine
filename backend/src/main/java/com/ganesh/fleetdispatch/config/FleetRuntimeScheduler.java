package com.ganesh.fleetdispatch.config;

import com.ganesh.fleetdispatch.dispatch.DashboardWebSocketServer;
import com.ganesh.fleetdispatch.dispatch.DriverFailureDetection;
import com.ganesh.fleetdispatch.dispatch.DriverFailureDetector;
import com.ganesh.fleetdispatch.dispatch.DriverRecoveryWorker;
import com.ganesh.fleetdispatch.dispatch.DriverStateStore;
import com.ganesh.fleetdispatch.dispatch.OrderStateStore;
import com.ganesh.fleetdispatch.dispatch.RecoveryAssignment;
import com.ganesh.fleetdispatch.persistence.DriverDocument;
import com.ganesh.fleetdispatch.persistence.DriverRepository;
import com.ganesh.fleetdispatch.persistence.OrderDocument;
import com.ganesh.fleetdispatch.persistence.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs the real heartbeat detector and queued picked-up-order recovery loop. */
@Component
public class FleetRuntimeScheduler {
    private static final Logger log = LoggerFactory.getLogger(FleetRuntimeScheduler.class);

    private final DriverFailureDetector failureDetector;
    private final DriverRecoveryWorker recoveryWorker;
    private final DashboardWebSocketServer dashboardWebSocketServer;
    private final DriverStateStore driverStateStore;
    private final OrderStateStore orderStateStore;
    private final DriverRepository driverRepository;
    private final OrderRepository orderRepository;

    public FleetRuntimeScheduler(
            DriverFailureDetector failureDetector,
            DriverRecoveryWorker recoveryWorker,
            DashboardWebSocketServer dashboardWebSocketServer,
            DriverStateStore driverStateStore,
            OrderStateStore orderStateStore,
            DriverRepository driverRepository,
            OrderRepository orderRepository) {
        this.failureDetector = failureDetector;
        this.recoveryWorker = recoveryWorker;
        this.dashboardWebSocketServer = dashboardWebSocketServer;
        this.driverStateStore = driverStateStore;
        this.orderStateStore = orderStateStore;
        this.driverRepository = driverRepository;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelay = 1_000L)
    public void processFleetRecovery() {
        List<DriverFailureDetection> detections = failureDetector.detect(System.currentTimeMillis());
        List<RecoveryAssignment> assignments = recoveryWorker.processBatch(20);

        for (DriverFailureDetection detection : detections) {
            driverStateStore.getDriver(detection.driverId())
                    .map(DriverDocument::from)
                    .ifPresent(driverRepository::save);
        }

        for (RecoveryAssignment assignment : assignments) {
            orderStateStore.getOrder(assignment.orderId())
                    .ifPresent(order -> orderRepository.save(
                            OrderDocument.from(order, assignment.driverId())));
            driverStateStore.getDriver(assignment.driverId())
                    .map(DriverDocument::from)
                    .ifPresent(driverRepository::save);
        }

        if (!detections.isEmpty() || !assignments.isEmpty()) {
            log.info("Fleet recovery tick: detectedFailures={}, recoveryAssignments={}",
                    detections.size(), assignments.size());
            dashboardWebSocketServer.broadcastSnapshot();
        }
    }
}
