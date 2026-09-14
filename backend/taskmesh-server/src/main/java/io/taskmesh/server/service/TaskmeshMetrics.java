package io.taskmesh.server.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TaskmeshMetrics {
    private final MeterRegistry registry;
    private final Counter submitted;
    private final Counter rejectedBackpressure;
    private final Counter requeued;
    private final Counter workerFailures;
    private final Timer queueSeconds;
    private final Timer schedulerCycle;

    public TaskmeshMetrics(MeterRegistry registry, JobRepository jobs, WorkerRepository workers,
                           BackpressureManager backpressure) {
        this.registry = registry;
        this.submitted = Counter.builder("taskmesh_jobs_submitted_total").register(registry);
        this.rejectedBackpressure =
                Counter.builder("taskmesh_jobs_rejected_backpressure_total").register(registry);
        this.requeued = Counter.builder("taskmesh_jobs_requeued_total").register(registry);
        this.workerFailures = Counter.builder("taskmesh_worker_failures_total").register(registry);
        this.queueSeconds = Timer.builder("taskmesh_job_queue_seconds").register(registry);
        this.schedulerCycle = Timer.builder("taskmesh_scheduler_cycle_seconds").register(registry);

        Gauge.builder("taskmesh_queue_depth", backpressure, BackpressureManager::getDepth)
                .register(registry);
        Gauge.builder("taskmesh_queue_head_age_seconds", jobs, repo ->
                        repo.findFirstByStatusOrderByCreatedAtAsc("QUEUED")
                                .map(head -> Math.max(0, Duration.between(head.getCreatedAt(),
                                        Instant.now()).toMillis() / 1000.0))
                                .orElse(0.0))
                .register(registry);
        for (String health : new String[]{"HEALTHY", "SUSPECTED", "FAILED"}) {
            Gauge.builder("taskmesh_workers", workers, repo -> (double) repo.countByHealth(health))
                    .tag("health", health)
                    .register(registry);
        }
    }

    public void jobSubmitted() {
        submitted.increment();
    }

    public void jobCompleted(String outcome) {
        Counter.builder("taskmesh_jobs_completed_total")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void rejectedBackpressure() {
        rejectedBackpressure.increment();
    }

    public void jobRequeued() {
        requeued.increment();
    }

    public void workerFailed() {
        workerFailures.increment();
    }

    public void recordQueueTime(Duration queueTime) {
        queueSeconds.record(queueTime);
    }

    public void recordCycle(Duration cycle) {
        schedulerCycle.record(cycle);
    }
}
