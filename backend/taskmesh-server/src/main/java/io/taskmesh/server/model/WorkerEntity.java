package io.taskmesh.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workers")
public class WorkerEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String address;

    @Column(name = "total_cpu", nullable = false)
    private int totalCpu;

    @Column(name = "total_memory_mb", nullable = false)
    private long totalMemoryMb;

    @Column(name = "available_cpu", nullable = false)
    private int availableCpu;

    @Column(name = "available_memory_mb", nullable = false)
    private long availableMemoryMb;

    @Column(name = "running_jobs", nullable = false)
    private int runningJobs;

    @Column(nullable = false)
    private String health;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "missed_beats", nullable = false)
    private int missedBeats;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public int getTotalCpu() { return totalCpu; }
    public void setTotalCpu(int totalCpu) { this.totalCpu = totalCpu; }
    public long getTotalMemoryMb() { return totalMemoryMb; }
    public void setTotalMemoryMb(long totalMemoryMb) { this.totalMemoryMb = totalMemoryMb; }
    public int getAvailableCpu() { return availableCpu; }
    public void setAvailableCpu(int availableCpu) { this.availableCpu = availableCpu; }
    public long getAvailableMemoryMb() { return availableMemoryMb; }
    public void setAvailableMemoryMb(long availableMemoryMb) { this.availableMemoryMb = availableMemoryMb; }
    public int getRunningJobs() { return runningJobs; }
    public void setRunningJobs(int runningJobs) { this.runningJobs = runningJobs; }
    public String getHealth() { return health; }
    public void setHealth(String health) { this.health = health; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public int getMissedBeats() { return missedBeats; }
    public void setMissedBeats(int missedBeats) { this.missedBeats = missedBeats; }
}
