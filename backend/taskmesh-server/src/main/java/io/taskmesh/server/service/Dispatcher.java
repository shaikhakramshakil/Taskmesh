package io.taskmesh.server.service;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;
import io.taskmesh.domain.model.WorkerHealth;
import io.taskmesh.domain.scheduling.SchedulingStrategy;
import io.taskmesh.server.model.JobEntity;
import io.taskmesh.server.model.WorkerEntity;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Dispatcher {
    /** Max QUEUED rows pulled into one pass: bounds memory, keeps the cycle O(1). */
    private static final int DISPATCH_BATCH = 500;
    private final JobRepository jobs;
    private final WorkerRepository workers;
    private final StrategyRegistry strategies;
    private final EventPort events;
    private final TaskmeshMetrics metrics;
    private final BackpressureManager backpressure;

    public Dispatcher(JobRepository jobs, WorkerRepository workers, StrategyRegistry strategies,
                      EventPort events, TaskmeshMetrics metrics, BackpressureManager backpressure) {
        this.jobs = jobs;
        this.workers = workers;
        this.strategies = strategies;
        this.events = events;
        this.metrics = metrics;
        this.backpressure = backpressure;
    }

    @Transactional
    public int dispatchOnce() {
        Instant start = Instant.now();
        try {
            List<JobEntity> queued = jobs.findByStatusOrderByCreatedAtAsc("QUEUED", PageRequest.of(0, DISPATCH_BATCH));
            if (queued.isEmpty()) {
                return 0;
            }
            List<WorkerEntity> healthy = workers.findByHealth("HEALTHY");
            if (healthy.isEmpty()) {
                return 0;
            }
            Map<String, JobEntity> byId = new LinkedHashMap<>();
            List<Job> domainJobs = new ArrayList<>(queued.size());
            for (JobEntity e : queued) {
                byId.put(e.getId(), e);
                domainJobs.add(toDomain(e));
            }
            Map<String, MutableCapacity> capacity = new LinkedHashMap<>();
            for (WorkerEntity w : healthy) {
                capacity.put(w.getId(), new MutableCapacity(w));
            }
            SchedulingStrategy strategy = strategies.active();
            List<Job> ordered = strategy.orderJobs(domainJobs);
            int placed = 0;
            for (Job job : ordered) {
                List<Worker> snapshots = capacity.values().stream()
                        .map(MutableCapacity::snapshot).toList();
                Worker selected = strategy.selectWorker(job, snapshots);
                if (selected == null) {
                    continue;
                }
                int cas = jobs.casAssign(job.id(), "QUEUED", "ASSIGNED", selected.id());
                if (cas == 0) {
                    continue;
                }
                // Reserve capacity with a CAS: the in-memory view may be stale
                // (concurrent dispatcher pass or racing heartbeat), and without
                // the predicate two instances can sell the same CPU twice.
                int reserved = workers.casReserve(selected.id(), job.requiredCpu(), job.requiredMemoryMb());
                if (reserved == 0) {
                    // The assignment above already moved to ASSIGNED: roll it back
                    // so a later pass can place it once capacity frees up.
                    jobs.casStatus(job.id(), "ASSIGNED", "QUEUED");
                    events.jobEvent("REQUEUED", job.id(), "stale capacity view for worker " + selected.id());
                    continue;
                }
                capacity.get(selected.id()).reserve(job.requiredCpu(), job.requiredMemoryMb());
                strategy.onDispatch(job);
                events.jobEvent("ASSIGNED", job.id(), "worker " + selected.id());
                placed++;
            }
            return placed;
        } finally {
            backpressure.refresh();
            metrics.recordCycle(Duration.between(start, Instant.now()));
        }
    }

    private static Job toDomain(JobEntity e) {
        return new Job(e.getId(), e.getName(), e.getPriority(), e.getCpu(), e.getMemoryMb(),
                e.getCreatedAt(), e.getDeadline(), e.getMetadata());
    }

    private static final class MutableCapacity {
        final String id;
        final String address;
        final int totalCpu;
        final long totalMemoryMb;
        int availableCpu;
        long availableMemoryMb;
        int runningJobs;

        MutableCapacity(WorkerEntity w) {
            this.id = w.getId();
            this.address = w.getAddress();
            this.totalCpu = w.getTotalCpu();
            this.totalMemoryMb = w.getTotalMemoryMb();
            this.availableCpu = w.getAvailableCpu();
            this.availableMemoryMb = w.getAvailableMemoryMb();
            this.runningJobs = w.getRunningJobs();
        }

        void reserve(int cpu, long memoryMb) {
            availableCpu = Math.max(0, availableCpu - cpu);
            availableMemoryMb = Math.max(0, availableMemoryMb - memoryMb);
            runningJobs++;
        }

        Worker snapshot() {
            return new Worker(id, address, totalCpu, totalMemoryMb, availableCpu,
                    availableMemoryMb, runningJobs, WorkerHealth.HEALTHY, Instant.now(), Instant.now());
        }
    }
}
