package com.ganesh.fleetdispatch.config;

import com.ganesh.fleetdispatch.dispatch.DashboardWebSocketServer;
import com.ganesh.fleetdispatch.dispatch.DriverFailureDetection;
import com.ganesh.fleetdispatch.dispatch.DriverFailureDetector;
import com.ganesh.fleetdispatch.dispatch.DriverRecoveryWorker;
import com.ganesh.fleetdispatch.dispatch.RecoveryAssignment;
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

    public FleetRuntimeScheduler(
            DriverFailureDetector failureDetector,
            DriverRecoveryWorker recoveryWorker,
            DashboardWebSocketServer dashboardWebSocketServer) {
        this.failureDetector = failureDetector;
        this.recoveryWorker = recoveryWorker;
        this.dashboardWebSocketServer = dashboardWebSocketServer;
    }

    @Scheduled(fixedDelay = 1_000L)
    public void processFleetRecovery() {
        List<DriverFailureDetection> detections = failureDetector.detect(System.currentTimeMillis());
        List<RecoveryAssignment> assignments = recoveryWorker.processBatch(20);

        if (!detections.isEmpty() || !assignments.isEmpty()) {
            log.info("Fleet recovery tick: detectedFailures={}, recoveryAssignments={}",
                    detections.size(), assignments.size());
            dashboardWebSocketServer.broadcastSnapshot();
        }
    }
}
