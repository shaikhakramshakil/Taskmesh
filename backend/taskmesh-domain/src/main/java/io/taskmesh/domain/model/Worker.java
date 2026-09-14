package io.taskmesh.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A worker registered with the mesh. Exposes the resource surface the schedulers fit jobs
 * onto: total/available CPU and memory, running job count, and health.
 */
public record Worker(
        String id,
        String address,
        int totalCpu,
        long totalMemoryMb,
        int availableCpu,
        long availableMemoryMb,
        int runningJobs,
        WorkerHealth health,
        Instant lastHeartbeat,
        Instant registeredAt) {

    /** Register a healthy worker exposing its full capacity. */
    public static Worker register(String id, String address, int totalCpu, long totalMemoryMb) {
        Instant now = Instant.now();
        return new Worker(id, address, totalCpu, totalMemoryMb, totalCpu, totalMemoryMb,
                0, WorkerHealth.HEALTHY, now, now);
    }

    /** Copy with a resource snapshot when a job is placed or completes. */
    public Worker withResources(int availableCpu, long availableMemoryMb, int runningJobs) {
        return new Worker(id, address, totalCpu, totalMemoryMb, availableCpu, availableMemoryMb,
                runningJobs, health, lastHeartbeat, registeredAt);
    }

    /** Copy with updated health and heartbeat timestamp. */
    public Worker withHealth(WorkerHealth health, Instant heartbeat) {
        return new Worker(id, address, totalCpu, totalMemoryMb, availableCpu, availableMemoryMb,
                runningJobs, health, heartbeat, registeredAt);
    }

    /**
     * Whether this worker can host {@code job} right now: healthy, and with enough free CPU
     * and memory.
     */
    public boolean canFit(Job job) {
        return health == WorkerHealth.HEALTHY
                && availableCpu >= job.requiredCpu()
                && availableMemoryMb >= job.requiredMemoryMb();
    }

    public double cpuUtilization() {
        return totalCpu == 0 ? 0.0 : (double) (totalCpu - availableCpu) / totalCpu;
    }

    public double memoryUtilization() {
        return totalMemoryMb == 0 ? 0.0 : (double) (totalMemoryMb - availableMemoryMb) / totalMemoryMb;
    }
}