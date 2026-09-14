package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.Comparator;
import java.util.List;

/**
 * Resource-aware: jobs are ordered by a composite score (<em>priority</em> + <em>deadline
 * pressure</em> + <em>queue age</em>, per the PRD evaluation model), and workers are chosen
 * best-fit — the eligible worker the job leaves least resource unused — so small jobs do not
 * occupy large workers and 16 GB jobs are never sent to a worker with 4 GB free.
 */
public final class ResourceAwareScheduler implements SchedulingStrategy {

    public static final double DEADLINE_PRESSURE_WEIGHT = 2.0;
    public static final double QUEUE_AGE_WEIGHT = 1e-3;
    private final java.util.function.LongSupplier now;

    /** Fresh scheduler whose "now" anchors the queue-age term. */
    public ResourceAwareScheduler() {
        this(System::currentTimeMillis);
    }

    /** Deterministic clock binding for tests. */
    public ResourceAwareScheduler(long nowMillis) {
        this(() -> nowMillis);
    }

    /** Live clock binding for simulations and long-lived coordinators. */
    public ResourceAwareScheduler(java.util.function.LongSupplier nowMillis) {
        this.now = nowMillis;
    }

    @Override
    public String name() {
        return "resource";
    }

    @Override
    public List<Job> orderJobs(List<Job> jobs) {
        return jobs.stream()
                .sorted(Comparator
                        .comparingDouble(this::macScore).reversed()
                        .thenComparing(Job::submittedAt)
                        .thenComparing(Job::id))
                .toList();
    }
    /** Higher = more urgent. Priority dominates; deadline pressure and queue age refine it. */
    public double macScore(Job job) {
        double deadlinePressure = 0.0;
        if (job.deadline() != null) {
            long remaining = job.deadline().toEpochMilli() - now.getAsLong();
            if (remaining <= 0) {
                deadlinePressure = 1.0;
            } else if (remaining < 60_000) {
                deadlinePressure = 1.0 - (remaining / 60_000.0); // ramps up inside the last minute
            }
        }
        long queueAgeMillis = Math.max(0, now.getAsLong() - job.submittedAt().toEpochMilli());
        return job.priority()
                + DEADLINE_PRESSURE_WEIGHT * deadlinePressure
                + QUEUE_AGE_WEIGHT * queueAgeMillis;
    }

    @Override
    public Worker selectWorker(Job job, List<Worker> workers) {
        return WorkerFit.bestFit(job, workers);
    }
}