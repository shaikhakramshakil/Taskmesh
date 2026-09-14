package io.taskmesh.server.service;

import io.taskmesh.server.model.JobEntity;
import io.taskmesh.server.model.WorkerEntity;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadlineSweep {
    private final JobRepository jobs;
    private final WorkerRepository workers;
    private final EventPort events;
    private final TaskmeshMetrics metrics;
    private final BackpressureManager backpressure;

    public DeadlineSweep(JobRepository jobs, WorkerRepository workers, EventPort events,
                         TaskmeshMetrics metrics, BackpressureManager backpressure) {
        this.jobs = jobs;
        this.workers = workers;
        this.events = events;
        this.metrics = metrics;
        this.backpressure = backpressure;
    }

    @Transactional
    public int sweepOnce() {
        Instant now = Instant.now();
        List<JobEntity> overdue =
                jobs.findByStatusInAndDeadlineBefore(List.of("QUEUED", "ASSIGNED"), now);
        for (JobEntity e : overdue) {
            if ("ASSIGNED".equals(e.getStatus()) && e.getWorkerId() != null) {
                workers.findById(e.getWorkerId()).ifPresent(w -> {
                    w.setAvailableCpu(Math.min(w.getTotalCpu(), w.getAvailableCpu() + e.getCpu()));
                    w.setAvailableMemoryMb(Math.min(w.getTotalMemoryMb(),
                            w.getAvailableMemoryMb() + e.getMemoryMb()));
                    w.setRunningJobs(Math.max(0, w.getRunningJobs() - 1));
                    workers.save(w);
                });
            }
            e.setStatus("CANCELLED");
            e.setError("DEADLINE_MISSED");
            e.setFinishedAt(now);
            jobs.save(e);
            events.jobEvent("DEADLINE_MISSED", e.getId(), "deadline " + e.getDeadline());
            metrics.jobCompleted("CANCELLED");
        }
        if (!overdue.isEmpty()) {
            backpressure.refresh();
        }
        return overdue.size();
    }
}
