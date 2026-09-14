package io.taskmesh.server.service;

import io.taskmesh.server.config.TaskmeshProperties;
import io.taskmesh.server.model.JobEntity;
import io.taskmesh.server.model.WorkerEntity;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import io.taskmesh.server.web.ApiExceptions;
import io.taskmesh.server.web.Dto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerRegistry {
    private final WorkerRepository workers;
    private final JobRepository jobs;
    private final EventPort events;
    private final TaskmeshMetrics metrics;
    private final RedisState redis;
    private final BackpressureManager backpressure;
    private final TaskmeshProperties props;

    public WorkerRegistry(WorkerRepository workers, JobRepository jobs, EventPort events,
                          TaskmeshMetrics metrics, RedisState redis,
                          BackpressureManager backpressure, TaskmeshProperties props) {
        this.workers = workers;
        this.jobs = jobs;
        this.events = events;
        this.metrics = metrics;
        this.redis = redis;
        this.backpressure = backpressure;
        this.props = props;
    }

    @Transactional
    public String register(String proposedId, String address, int totalCpu, long totalMemoryMb) {
        String id = proposedId == null || proposedId.isBlank() ? UUID.randomUUID().toString() : proposedId;
        WorkerEntity w = workers.findById(id).orElseGet(WorkerEntity::new);
        w.setId(id);
        w.setAddress(address);
        w.setTotalCpu(totalCpu);
        w.setTotalMemoryMb(totalMemoryMb);
        w.setAvailableCpu(totalCpu);
        w.setAvailableMemoryMb(totalMemoryMb);
        w.setRunningJobs(0);
        w.setHealth("HEALTHY");
        w.setLastHeartbeat(Instant.now());
        w.setMissedBeats(0);
        workers.save(w);
        return id;
    }

    @Transactional
    public Dto.HeartbeatResponse heartbeat(String workerId, int availableCpu,
                                           long availableMemoryMb, int runningJobs) {
        WorkerEntity w = workers.findById(workerId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Unknown worker: " + workerId));
        w.setAvailableCpu(Math.min(availableCpu, w.getTotalCpu()));
        w.setAvailableMemoryMb(Math.min(availableMemoryMb, w.getTotalMemoryMb()));
        w.setRunningJobs(Math.max(0, runningJobs));
        w.setLastHeartbeat(Instant.now());
        w.setMissedBeats(0);
        if (!"HEALTHY".equals(w.getHealth())) {
            w.setHealth("HEALTHY");
        }
        workers.save(w);
        redis.heartbeat(workerId);

        List<Dto.AssignmentView> assignments = new ArrayList<>();
        for (JobEntity job : jobs.findByWorkerIdAndStatus(workerId, "ASSIGNED")) {
            assignments.add(new Dto.AssignmentView(job.getId(), job.getName(), job.getPriority(),
                    job.getCpu(), job.getMemoryMb(), job.getDeadline()));
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            jobs.save(job);
            metrics.recordQueueTime(Duration.between(job.getCreatedAt(), job.getStartedAt()));
            events.jobEvent("STARTED", job.getId(), "worker " + workerId);
        }
        List<String> cancelIds = jobs.findByWorkerIdAndStatus(workerId, "RUNNING").stream()
                .filter(JobEntity::isCancelRequested)
                .map(JobEntity::getId)
                .toList();
        return new Dto.HeartbeatResponse(w.getHealth(), assignments, cancelIds);
    }

    @Transactional(readOnly = true)
    public List<Dto.WorkerView> list() {
        return workers.findAll().stream().map(Dto.WorkerView::from).toList();
    }

    /** Missed-beat detector: SUSPECTED after N missed beats, FAILED after M, with requeue. */
    @Transactional
    public void checkHeartbeats() {
        Instant now = Instant.now();
        long beatSeconds = Math.max(1, props.getHeartbeat().getBeatSeconds());
        int suspectAt = props.getHeartbeat().getSuspectMissed();
        int failAt = props.getHeartbeat().getFailMissed();
        for (WorkerEntity w : workers.findAll()) {
            long missed = Duration.between(w.getLastHeartbeat(), now).getSeconds() / beatSeconds;
            if (missed < 0) {
                missed = 0;
            }
            if (missed >= failAt && !"FAILED".equals(w.getHealth())) {
                int updated = workers.casMissedBeats(w.getId(), w.getMissedBeats(),
                        (int) missed, "FAILED");
                if (updated == 0) {
                    continue;
                }
                failWorker(w.getId(), now);
            } else if (missed >= suspectAt && "HEALTHY".equals(w.getHealth())) {
                int updated = workers.casMissedBeats(w.getId(), w.getMissedBeats(),
                        (int) missed, "SUSPECTED");
                if (updated == 0) {
                    continue;
                }
                events.opsEvent("WORKER_SUSPECTED",
                        "worker " + w.getId() + " missed " + missed + " beats");
            } else if (missed != w.getMissedBeats()
                    && ("SUSPECTED".equals(w.getHealth()) || "FAILED".equals(w.getHealth()))) {
                workers.casMissedBeats(w.getId(), w.getMissedBeats(), (int) missed, w.getHealth());
            }
        }
    }

    private void failWorker(String workerId, Instant now) {
        List<JobEntity> owned = jobs.findByWorkerIdAndStatusIn(workerId, List.of("ASSIGNED", "RUNNING"));
        for (JobEntity job : owned) {
            job.setAttempts(job.getAttempts() + 1);
            if (job.getAttempts() < job.getMaxAttempts()) {
                job.setStatus("QUEUED");
                job.setWorkerId(null);
                job.setStartedAt(null);
                jobs.save(job);
                events.jobEvent("REQUEUED", job.getId(), "worker " + workerId + " failed");
                metrics.jobRequeued();
            } else {
                job.setStatus("FAILED");
                job.setError("WORKER_FAILED");
                job.setFinishedAt(now);
                jobs.save(job);
                events.jobEvent("FAILED", job.getId(), "worker " + workerId + " failed");
                metrics.jobCompleted("FAILED");
            }
        }
        workers.findById(workerId).ifPresent(w -> {
            w.setAvailableCpu(w.getTotalCpu());
            w.setAvailableMemoryMb(w.getTotalMemoryMb());
            w.setRunningJobs(0);
            workers.save(w);
        });
        events.opsEvent("WORKER_FAILED", "worker " + workerId + " failed");
        metrics.workerFailed();
        backpressure.refresh();
    }
}
