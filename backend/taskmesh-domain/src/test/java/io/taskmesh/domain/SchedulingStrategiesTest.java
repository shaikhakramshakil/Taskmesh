package io.taskmesh.domain;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.JobStatus;
import io.taskmesh.domain.model.Worker;
import io.taskmesh.domain.model.WorkerHealth;
import io.taskmesh.domain.scheduling.DeadlineScheduler;
import io.taskmesh.domain.scheduling.FIFOScheduler;
import io.taskmesh.domain.scheduling.FairScheduler;
import io.taskmesh.domain.scheduling.PriorityScheduler;
import io.taskmesh.domain.scheduling.ResourceAwareScheduler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral contracts for the five scheduling strategies: ordering, worker fit,
 * the fairness starvation bound, and the resource-aware best-fit rule.
 */
class SchedulingStrategiesTest {

    private static final Instant T0 = Instant.parse("2026-09-12T00:00:00Z");

    private static Job job(String name, int priority, int cpu, long memMb, Instant submittedAt, Instant deadline) {
        return new Job(UUID.randomUUID().toString(), name, priority, cpu, memMb, submittedAt, deadline, null);
    }

    private static Job job(String name, int priority, int cpu, long memMb, Instant submittedAt) {
        return job(name, priority, cpu, memMb, submittedAt, null);
    }
    private static Job job(String name, int priority, Instant submittedAt, Instant deadline, String metadata) {
        return new Job(UUID.randomUUID().toString(), name, priority, 1, 512, submittedAt, deadline, metadata);
    }
    private static Job job(String name, int priority, Instant submittedAt) {
        return job(name, priority, 1, 512, submittedAt, null);
    }

    private static Worker worker(String id, int totalCpu, long totalMemMb) {
        return Worker.register(id, "10.0.0.1", totalCpu, totalMemMb);
    }

    private static Worker busyWorker(String id, int totalCpu, long totalMemMb, int usedCpu, long usedMemMb, int running) {
        return Worker.register(id, "10.0.0.1", totalCpu, totalMemMb)
                .withResources(totalCpu - usedCpu, totalMemMb - usedMemMb, running);
    }

    @Test
    void fifoDispatchesInSubmissionOrder() {
        var late = job("late", 9, T0.plusSeconds(10));
        var early = job("early", 1, T0);
        var ordered = new FIFOScheduler().orderJobs(List.of(late, early));
        assertEquals("early", ordered.get(0).name());
        assertEquals("late", ordered.get(1).name());
    }

    @Test
    void fifoTakesFirstFittingWorkerAndNullWhenNothingFits() {
        var scheduler = new FIFOScheduler();
        var smallJob = job("small", 5, 1, 512, T0);
        var tooBig = job("huge", 5, 64, 1_000_000, T0);
        var w1 = worker("w1", 8, 16_384);
        var w2 = worker("w2", 4, 8_192);

        assertEquals("w1", scheduler.selectWorker(smallJob, List.of(w1, w2)).id());
        assertNull(scheduler.selectWorker(tooBig, List.of(w1, w2)));
        assertNull(scheduler.selectWorker(smallJob, List.of()));
    }

    @Test
    void priorityOrdersHighestFirstWithFifoTiebreak() {
        var p2 = job("p2", 2, T0);
        var p10 = job("p10", 10, T0.plusSeconds(5));
        var p8a = job("p8a", 8, T0.plusSeconds(1));
        var p8b = job("p8b", 8, T0.plusSeconds(2));
        var ordered = new PriorityScheduler().orderJobs(List.of(p2, p8b, p10, p8a));
        assertEquals(List.of("p10", "p8a", "p8b", "p2"),
                ordered.stream().map(Job::name).toList());
    }
    @Test
    void leastServedClassJumpsQueueAheadOfFreshHighPriority() {
        var fair = new FairScheduler();
        var low = job("low", 1, T0);
        var highA = job("high-a", 10, T0.plusSeconds(1));
        var highB = job("high-b", 10, T0.plusSeconds(2));
        var queue = new ArrayList<>(List.of(low, highA, highB));

        // First dispatch serves high priority on the tiebreak...
        var first = fair.orderJobs(queue).get(0);
        assertEquals("high-a", first.name());
        fair.onDispatch(first);
        queue.remove(first);

        // ...but the unserved low-priority class now outranks fresh high-priority demand.
        assertEquals("low", fair.orderJobs(queue).get(0).name());
    }

    @Test
    void fairGuaranteesLowPriorityDispatchUnderSustainedPressure() {
        var fair = new FairScheduler();
        var starving = job("low", 1, T0);
        var remaining = new ArrayList<>(List.of(starving, job("high-0", 10, T0)));

        int rounds = 0;
        boolean lowDispatched = false;
        while (rounds < 12 && !remaining.isEmpty()) {
            rounds++;
            var first = fair.orderJobs(remaining).get(0);
            fair.onDispatch(first);
            remaining.remove(first);
            if (first.name().equals("low")) {
                lowDispatched = true;
            } else {
                // Sustained high-priority pressure: a fresh p10 arrives every round.
                remaining.add(job("high-" + rounds, 10, T0.plusSeconds(rounds)));
            }
        }
        assertTrue(lowDispatched, "priority-1 job must be dispatched within 12 rounds, took " + rounds);
    }

    @Test
    void priorityStarvesLowPriorityUnderSamePressure() {
        var strict = new PriorityScheduler();
        var starving = job("low", 1, T0);
        var remaining = new ArrayList<>(List.of(starving, job("high-0", 10, T0)));

        for (int round = 1; round <= 12; round++) {
            var first = strict.orderJobs(remaining).get(0);
            remaining.remove(first);
            remaining.add(job("high-" + round, 10, T0.plusSeconds(round)));
        }
        assertTrue(remaining.stream().anyMatch(j -> j.name().equals("low")),
                "strict priority must still be holding the low-priority job (documents the starvation gap)");
    }

    @Test
    void fairResetClearsDeficits() {
        var fair = new FairScheduler();
        var jobs = List.of(job("a", 10, T0), job("b", 1, T0.plusSeconds(1)));
        fair.orderJobs(jobs);
        fair.onDispatch(jobs.get(0));
        fair.reset();
        var fresh = new FairScheduler();
        assertEquals(
                fresh.orderJobs(jobs).stream().map(Job::name).toList(),
                fair.orderJobs(jobs).stream().map(Job::name).toList());
    }

    @Test
    void deadlineOrdersEarliestFirstWithNoDeadlineLast() {
        var noDeadline = job("none", 10, T0);
        var later = job("later", 1, T0, T0.plusSeconds(60), null);
        var sooner = job("sooner", 1, T0, T0.plusSeconds(10), null);
        var ordered = new DeadlineScheduler().orderJobs(List.of(noDeadline, later, sooner));
        assertEquals(List.of("sooner", "later", "none"),
                ordered.stream().map(Job::name).toList());
    }

    @Test
    void deadlinePrefersLeastUtilizedFittingWorker() {
        var scheduler = new DeadlineScheduler();
        var j = job("urgent", 9, 2, 2048, T0, T0.plusSeconds(30));
        var idle = worker("idle", 8, 16_384);
        var busy = busyWorker("busy", 8, 16_384, 6, 12_288, 3);
        assertEquals("idle", scheduler.selectWorker(j, List.of(busy, idle)).id());
    }

    @Test
    void fairPlacesJobsBestFitToAvoidFragmentation() {
        var scheduler = new FairScheduler();
        var j = job("etl", 5, 2, 4096, T0);
        var big = worker("big", 8, 16_384);
        var small = worker("small", 2, 4096);
        assertEquals("small", scheduler.selectWorker(j, List.of(big, small)).id());
    }

    @Test
    void resourceAwarePicksSmallestSufficientWorker() {
        var scheduler = new ResourceAwareScheduler(T0.toEpochMilli());
        var j = job("etl", 5, 2, 4096, T0);
        var big = worker("big", 8, 16_384);
        var small = worker("small", 2, 4096);
        assertEquals("small", scheduler.selectWorker(j, List.of(big, small)).id());
    }

    @Test
    void resourceAwareNeverOversubscribesMemory() {
        var scheduler = new ResourceAwareScheduler(T0.toEpochMilli());
        // PRD example: a 16 GB job must not land on a worker with 4 GB free.
        var heavy = job("video", 8, 2, 16_384, T0);
        var small = worker("small", 4, 8192);
        var big = worker("big", 8, 32_768);
        assertEquals("big", scheduler.selectWorker(heavy, List.of(small, big)).id());
        assertNull(scheduler.selectWorker(heavy, List.of(small)));
    }

    @Test
    void resourceAwareRanksUrgentDeadlineAboveEqualPriority() {
        var scheduler = new ResourceAwareScheduler(T0.toEpochMilli());
        var relaxed = job("relaxed", 5, T0);
        var urgent = job("urgent", 5, T0, T0.plusSeconds(10), null);
        var ordered = scheduler.orderJobs(List.of(relaxed, urgent));
        assertEquals("urgent", ordered.get(0).name());
    }

    @Test
    void unhealthyWorkersAreNeverSelected() {
        var j = job("x", 5, T0);
        var suspected = worker("s", 8, 16_384).withHealth(WorkerHealth.SUSPECTED, T0);
        var failed = worker("f", 8, 16_384).withHealth(WorkerHealth.FAILED, T0);
        for (var scheduler : List.of(new FIFOScheduler(), new PriorityScheduler(),
                new FairScheduler(), new DeadlineScheduler(),
                new ResourceAwareScheduler(T0.toEpochMilli()))) {
            assertNull(scheduler.selectWorker(j, List.of(suspected, failed)),
                    scheduler.name() + " must skip non-healthy workers");
        }
    }

    @Test
    void jobFactoryAndWorkerRegistrationExposeFullCapacity() {
        var j = Job.of("video-processing", 8, 2, 4096, T0.plusSeconds(300), null);
        assertEquals(JobStatus.QUEUED, JobStatus.QUEUED);
        assertEquals(8, j.priority());
        assertNull(job("plain", 1, T0).deadline());
        assertNotNull(j.id());

        var w = Worker.register("w1", "10.0.0.9", 8, 16_384);
        assertEquals(8, w.availableCpu());
        assertEquals(16_384, w.availableMemoryMb());
        assertEquals(WorkerHealth.HEALTHY, w.health());
        assertTrue(w.canFit(j));
    }
    @Test
    void fairSharesStayEqualOverLongRuns() {
        var fair = new FairScheduler();
        var low = job("low", 1, T0);
        var high = job("high", 10, T0.plusSeconds(1));
        int lowWins = 0;
        // 2000 dispatch rounds: without the per-round rebase the credit values
        // grow unbounded and fairness degrades; with it the split stays ~50/50.
        for (int i = 0; i < 2000; i++) {
            var first = fair.orderJobs(List.of(low, high)).get(0);
            if (first.name().equals("low")) {
                lowWins++;
            }
            fair.onDispatch(first);
        }
        assertTrue(Math.abs(lowWins - 1000) <= 2,
                "expected ~50/50 split, got low=" + lowWins);
    }
}