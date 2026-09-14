package io.taskmesh.worker;

import java.net.InetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "taskmesh.worker")
public class WorkerProperties {
    private String id;
    private String address;
    private int cpu = 4;
    private long memoryMb = 8192;
    private double failureRate = 0.0;
    private long heartbeatIntervalMs = 2000;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public long getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(long memoryMb) {
        this.memoryMb = memoryMb;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    /** Stable id for registration; defaults to hostname-PID when unconfigured. */
    public synchronized String effectiveId() {
        if (id == null || id.isBlank()) {
            id = shortHostname() + "-" + ProcessHandle.current().pid();
        }
        return id;
    }

    public synchronized String effectiveAddress() {
        if (address == null || address.isBlank()) {
            address = shortHostname();
        }
        return address;
    }

    private static String shortHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
