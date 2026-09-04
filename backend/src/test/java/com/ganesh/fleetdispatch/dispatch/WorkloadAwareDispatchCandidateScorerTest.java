package com.ganesh.fleetdispatch.dispatch;

import com.ganesh.fleetdispatch.graph.NodeId;
import com.ganesh.fleetdispatch.graph.Route;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadAwareDispatchCandidateScorerTest {

    private static final Route ROUTE = new Route(
            List.of(new NodeId(1L), new NodeId(2L)),
            10.0,
            500.0);

    @Test
    void penalizesDriversWithMoreCommittedAssignments() {
        WorkloadAwareDispatchCandidateScorer scorer =
                new WorkloadAwareDispatchCandidateScorer(1.0, 0.0, 5.0);
        Driver lightlyLoaded = new Driver(1L, new NodeId(1L), DriverStatus.AVAILABLE);
        Driver heavilyLoaded = new Driver(2L, new NodeId(2L), DriverStatus.AVAILABLE);

        scorer.onAssignmentCommitted(heavilyLoaded);
        scorer.onAssignmentCommitted(heavilyLoaded);

        double lightScore = scorer.score(new DriverCandidate(lightlyLoaded, 100.0), ROUTE);
        double heavyScore = scorer.score(new DriverCandidate(heavilyLoaded, 100.0), ROUTE);

        assertEquals(10.0, scorer.assignmentCount(lightlyLoaded.id()));
        assertEquals(2L, scorer.assignmentCount(heavilyLoaded.id()));
        assertEquals(10.0, lightScore);
        assertEquals(20.0, heavyScore);
        assertTrue(lightScore < heavyScore);
    }

    @Test
    void concurrentAssignmentUpdatesAreCountedExactly() throws InterruptedException {
        WorkloadAwareDispatchCandidateScorer scorer =
                new WorkloadAwareDispatchCandidateScorer(1.0, 0.0, 1.0);
        Driver driver = new Driver(7L, new NodeId(7L), DriverStatus.AVAILABLE);

        int threads = 8;
        int updatesPerThread = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int j = 0; j < updatesPerThread; j++) {
                            scorer.onAssignmentCommitted(driver);
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
        } finally {
            executor.shutdownNow();
        }

        assertEquals((long) threads * updatesPerThread, scorer.assignmentCount(driver.id()));
    }

    @Test
    void rejectsInvalidWeights() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new WorkloadAwareDispatchCandidateScorer(-1.0, 0.0, 1.0));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new WorkloadAwareDispatchCandidateScorer(0.0, 0.0, 0.0));
    }
}
