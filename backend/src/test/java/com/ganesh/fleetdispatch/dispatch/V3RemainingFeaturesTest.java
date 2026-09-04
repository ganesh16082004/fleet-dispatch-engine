package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class V3RemainingFeaturesTest {
    @Test
    void domainEventsArePublishedToSubscribers() {
        DomainEventBus bus = new DomainEventBus();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe(received::add);

        bus.publish(new DriverFailedEvent(7, 1000));

        assertEquals(1, received.size());
        assertInstanceOf(DriverFailedEvent.class, received.get(0));
    }

    @Test
    void idempotentProcessorRunsAnEventOnlyOnce() {
        IdempotencyStore store = new InMemoryIdempotencyStore();
        IdempotentEventProcessor processor = new IdempotentEventProcessor(store);
        DomainEvent event = new DriverFailedEvent(1, 50);
        int[] calls = {0};

        assertTrue(processor.process("event-1", event, ignored -> calls[0]++));
        assertFalse(processor.process("event-1", event, ignored -> calls[0]++));
        assertEquals(1, calls[0]);
        assertTrue(store.contains("event-1"));
    }

    @Test
    void retryPolicyUsesExponentialCappedBackoff() {
        RecoveryRetryPolicy policy = new RecoveryRetryPolicy(100, 2.0, 350, 5);
        assertEquals(100, policy.delayMillis(1));
        assertEquals(200, policy.delayMillis(2));
        assertEquals(350, policy.delayMillis(3));
        assertTrue(policy.exhausted(5));
    }

    @Test
    void retrySchedulerReleasesTasksOnlyWhenDue() {
        RecoveryRetryScheduler scheduler = new RecoveryRetryScheduler(
                new RecoveryRetryPolicy(100, 2.0, 1000, 4));
        DriverRecoveryTask task = new DriverRecoveryTask(1, 2, new NodeId(3), 0);

        assertTrue(scheduler.schedule(task, 0));
        assertTrue(scheduler.drainDue(99, 1).isEmpty());
        assertEquals(List.of(task), scheduler.drainDue(100, 1));
        assertEquals(1, scheduler.attempts(2));
        scheduler.markSucceeded(2);
        assertEquals(0, scheduler.attempts(2));
    }

    @Test
    void etaEngineProducesDeterministicEstimate() {
        EtaEngine eta = new EtaEngine(10, 30, 20);
        Route route = new Route(List.of(new NodeId(1), new NodeId(2)), 40, 100);

        assertEquals(40.0, eta.estimateSeconds(route));
        assertEquals(70.0, eta.estimateStopSeconds(route, RouteStopType.PICKUP));
    }

    @Test
    void metricsSnapshotIsConsistent() {
        DispatchMetrics metrics = new DispatchMetrics();
        metrics.increment("assignments");
        metrics.add("assignments", 2);
        metrics.increment("recoveries");

        assertEquals(3, metrics.count("assignments"));
        assertEquals(1, metrics.count("recoveries"));
        assertEquals(2, metrics.snapshot().size());
    }

    @Test
    void loadSimulatorIsDeterministicForSameSeed() {
        DeterministicLoadSimulator simulator = new DeterministicLoadSimulator();
        var first = simulator.generate(3, 5, 42, 1000, 20);
        var second = simulator.generate(3, 5, 42, 1000, 20);

        assertEquals(first, second);
        assertEquals(3, first.drivers().size());
        assertEquals(5, first.orders().size());
    }
}
