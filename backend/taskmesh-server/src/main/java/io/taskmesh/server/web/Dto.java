package io.taskmesh.server.web;

import io.taskmesh.server.model.JobEntity;
import io.taskmesh.server.model.WorkerEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Dto {
    private Dto() {
    }

    public record SubmitJobRequest(
            @NotBlank String name,
            @NotNull @Min(1) @Max(10) Integer priority,
            @NotNull @Min(1) Integer cpu,
            @NotNull @Min(1) Long memoryMb,
            Instant deadline,
            String metadata,
            @Min(1) Integer maxAttempts) {
    }

    public record JobView(
            String id, String name, int priority, int cpu, long memoryMb,
            Instant deadline, String status, String workerId,
            int attempts, int maxAttempts,
            Instant createdAt, Instant startedAt, Instant finishedAt, String error) {
        public static JobView from(JobEntity e) {
            return new JobView(e.getId(), e.getName(), e.getPriority(), e.getCpu(), e.getMemoryMb(),
                    e.getDeadline(), e.getStatus(), e.getWorkerId(),
                    e.getAttempts(), e.getMaxAttempts(),
                    e.getCreatedAt(), e.getStartedAt(), e.getFinishedAt(), e.getError());
        }
    }

    public record RegisterWorkerRequest(String id, String address,
                                        @Min(1) int totalCpu, @Min(1) long totalMemoryMb) {
    }

    public record HeartbeatRequest(int availableCpu, long availableMemoryMb, int runningJobs) {
    }

    public record AssignmentView(String jobId, String name, int priority, int cpu,
                                 long memoryMb, Instant deadline) {
    }

    public record HeartbeatResponse(String health, List<AssignmentView> assignments,
                                    List<String> cancelIds) {
    }

    public record CompleteRequest(String outcome, String error) {
    }

    public record WorkerView(String id, String address, int totalCpu, long totalMemoryMb,
                             int availableCpu, long availableMemoryMb, int runningJobs,
                             String health, Instant lastHeartbeat) {
        public static WorkerView from(WorkerEntity e) {
            return new WorkerView(e.getId(), e.getAddress(), e.getTotalCpu(), e.getTotalMemoryMb(),
                    e.getAvailableCpu(), e.getAvailableMemoryMb(), e.getRunningJobs(),
                    e.getHealth(), e.getLastHeartbeat());
        }
    }

    public record QueueView(long depth, long threshold, boolean backpressureActive,
                            long rejectedTotal) {
    }

    public record StrategyInfo(String name, String description) {
    }

    public record StrategiesView(String active, List<StrategyInfo> strategies) {
    }

    public record ActiveStrategyRequest(String name) {
    }

    public record SimulateRequest(Integer jobs, Long seed, Double arrivalRate,
                                  List<String> strategies, String workers) {
    }

    public record SummaryView(QueueSummary queue, Map<String, Long> jobs,
                              Map<String, Long> workers, String activeStrategy) {
    }

    public record QueueSummary(long depth, long threshold, boolean backpressureActive) {
    }

    public record ErrorBody(String error, String message) {
    }
}
