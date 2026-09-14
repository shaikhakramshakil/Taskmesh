package io.taskmesh.server.service;

import io.taskmesh.server.model.JobEntity;
import io.taskmesh.server.model.WorkerEntity;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import io.taskmesh.server.web.ApiExceptions;
import io.taskmesh.server.web.Dto;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final JobRepository jobs;
    private final WorkerRepository workers;
    private final BackpressureManager backpressure;
    private final EventPort events;
    private final TaskmeshMetrics metrics;

    public JobService(JobRepository jobs, WorkerRepository workers,
                      BackpressureManager backpressure, EventPort events, TaskmeshMetrics metrics) {
        this.jobs = jobs;
        this.workers = workers;
        this.backpressure = backpressure;
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional
    public Dto.JobView submit(Dto.SubmitJobRequest req) {
        backpressure.check(req.priority());
        JobEntity e = new JobEntity();
        e.setId(UUID.randomUUID().toString());
        e.setName(req.name());
        e.setPriority(req.priority());
        e.setCpu(req.cpu());
        e.setMemoryMb(req.memoryMb());
        e.setDeadline(req.deadline());
        e.setMetadata(req.metadata());
        e.setStatus("QUEUED");
        e.setAttempts(1);
        e.setMaxAttempts(req.maxAttempts() == null ? 3 : req.maxAttempts());
        e.setCreatedAt(Instant.now());
        e.setCancelRequested(false);
        jobs.save(e);
        metrics.jobSubmitted();
        events.jobEvent("SUBMITTED", e.getId(), e.getName());
        backpressure.refresh();
        return Dto.JobView.from(e);
    }

    @Transactional(readOnly = true)
    public List<Dto.JobView> list(String status, int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        PageRequest page = PageRequest.of(0, capped);
        List<JobEntity> found = status == null
                ? jobs.findAllByOrderByCreatedAtDesc(page)
                : jobs.findByStatusOrderByCreatedAtDesc(status, page);
        return found.stream().map(Dto.JobView::from).toList();
    }

    @Transactional(readOnly = true)
    public Dto.JobView get(String id) {
        return Dto.JobView.from(require(id));
    }

    @Transactional
    public Dto.JobView cancel(String id) {
        JobEntity e = require(id);
        switch (e.getStatus()) {
            case "QUEUED", "ASSIGNED" -> {
                if ("ASSIGNED".equals(e.getStatus()) && e.getWorkerId() != null) {
                    releaseReservation(e);
                }
                e.setStatus("CANCELLED");
                e.setFinishedAt(Instant.now());
                e.setCancelRequested(true);
                jobs.save(e);
                events.jobEvent("CANCELLED", e.getId(), "cancel requested");
                metrics.jobCompleted("CANCELLED");
                backpressure.refresh();
                return Dto.JobView.from(e);
            }
            case "RUNNING" -> {
                e.setCancelRequested(true);
                jobs.save(e);
                return Dto.JobView.from(e);
            }
            default -> throw new ApiExceptions.ConflictException(
                    "Job " + id + " is already terminal (" + e.getStatus() + ")");
        }
    }

    @Transactional
    public Dto.JobView complete(String workerId, String jobId, String outcome, String error) {
        WorkerEntity worker = workers.findById(workerId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Unknown worker: " + workerId));
        JobEntity e = jobs.findById(jobId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Unknown job: " + jobId));
        if (!workerId.equals(e.getWorkerId())) {
            throw new ApiExceptions.ConflictException(
                    "Job " + jobId + " is not owned by worker " + workerId);
        }
        if (TERMINAL.contains(e.getStatus())) {
            return Dto.JobView.from(e);
        }
        String normalized = outcome == null ? "" : outcome.toUpperCase();
        switch (normalized) {
            case "COMPLETED" -> {
                freeCapacity(worker, e);
                e.setStatus("COMPLETED");
                e.setFinishedAt(Instant.now());
                workers.save(worker);
                jobs.save(e);
                events.jobEvent("COMPLETED", e.getId(), null);
                metrics.jobCompleted("COMPLETED");
            }
            case "CANCELLED" -> {
                freeCapacity(worker, e);
                e.setStatus("CANCELLED");
                e.setFinishedAt(Instant.now());
                workers.save(worker);
                jobs.save(e);
                events.jobEvent("CANCELLED", e.getId(),
                        error == null ? "aborted by worker" : error);
                metrics.jobCompleted("CANCELLED");
            }
            case "FAILED" -> {
                freeCapacity(worker, e);
                workers.save(worker);
                e.setAttempts(e.getAttempts() + 1);
                if (e.getAttempts() < e.getMaxAttempts()) {
                    e.setStatus("QUEUED");
                    e.setWorkerId(null);
                    e.setStartedAt(null);
                    jobs.save(e);
                    events.jobEvent("REQUEUED", e.getId(),
                            "attempt " + e.getAttempts() + ": " + error);
                    metrics.jobRequeued();
                } else {
                    e.setStatus("FAILED");
                    e.setError(error);
                    e.setFinishedAt(Instant.now());
                    jobs.save(e);
                    events.jobEvent("FAILED", e.getId(), error);
                    metrics.jobCompleted("FAILED");
                }
            }
            default -> throw new ApiExceptions.BadRequestException(
                    "Unknown outcome: '" + outcome + "'. Expected COMPLETED|FAILED|CANCELLED.");
        }
        backpressure.refresh();
        return Dto.JobView.from(require(e.getId()));
    }

    private JobEntity require(String id) {
        return jobs.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Unknown job: " + id));
    }

    private void releaseReservation(JobEntity e) {
        workers.findById(e.getWorkerId()).ifPresent(w -> {
            freeCapacity(w, e);
            workers.save(w);
        });
    }

    private void freeCapacity(WorkerEntity w, JobEntity e) {
        w.setAvailableCpu(Math.min(w.getTotalCpu(), w.getAvailableCpu() + e.getCpu()));
        w.setAvailableMemoryMb(
                Math.min(w.getTotalMemoryMb(), w.getAvailableMemoryMb() + e.getMemoryMb()));
        w.setRunningJobs(Math.max(0, w.getRunningJobs() - 1));
    }
}
