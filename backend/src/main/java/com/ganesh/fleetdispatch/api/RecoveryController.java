package com.ganesh.fleetdispatch.api;

import com.ganesh.fleetdispatch.dispatch.DriverFailureDetection;
import com.ganesh.fleetdispatch.dispatch.DriverFailureRecoveryCoordinator;
import com.ganesh.fleetdispatch.dispatch.DriverRecoveryQueue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/recovery")
public class RecoveryController {
    private final DriverFailureRecoveryCoordinator recoveryCoordinator;
    private final DriverRecoveryQueue recoveryQueue;

    public RecoveryController(
            DriverFailureRecoveryCoordinator recoveryCoordinator,
            DriverRecoveryQueue recoveryQueue) {
        this.recoveryCoordinator = recoveryCoordinator;
        this.recoveryQueue = recoveryQueue;
    }

    /** Runs the same backend failure workflow used by the heartbeat detector. */
    @PostMapping("/drivers/{driverId}/fail")
    public DriverFailureDetection simulateFailure(@PathVariable long driverId) {
        return recoveryCoordinator.recover(driverId, System.currentTimeMillis());
    }

    @GetMapping("/queue")
    public Map<String, Object> queueStatus() {
        return Map.of("pendingRecoveryTasks", recoveryQueue.size());
    }
}
