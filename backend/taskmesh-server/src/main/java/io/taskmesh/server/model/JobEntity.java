package io.taskmesh.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priority;

    @Column(name = "cpu", nullable = false)
    private int cpu;

    @Column(name = "memory_mb", nullable = false)
    private long memoryMb;

    @Column
    private Instant deadline;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private String status;

    @Column(name = "worker_id")
    private String workerId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column
    private String error;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getCpu() { return cpu; }
    public void setCpu(int cpu) { this.cpu = cpu; }
    public long getMemoryMb() { return memoryMb; }
    public void setMemoryMb(long memoryMb) { this.memoryMb = memoryMb; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean cancelRequested) { this.cancelRequested = cancelRequested; }
}
