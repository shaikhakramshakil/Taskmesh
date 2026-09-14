package io.taskmesh.server.web;

import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import io.taskmesh.server.service.BackpressureManager;
import io.taskmesh.server.service.StrategyRegistry;
import io.taskmesh.server.web.Dto.ActiveStrategyRequest;
import io.taskmesh.server.web.Dto.QueueSummary;
import io.taskmesh.server.web.Dto.QueueView;
import io.taskmesh.server.web.Dto.StrategiesView;
import io.taskmesh.server.web.Dto.StrategyInfo;
import io.taskmesh.server.web.Dto.SummaryView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OpsController {
    private final BackpressureManager backpressure;
    private final StrategyRegistry strategies;
    private final JobRepository jobs;
    private final WorkerRepository workers;

    public OpsController(BackpressureManager backpressure, StrategyRegistry strategies,
                         JobRepository jobs, WorkerRepository workers) {
        this.backpressure = backpressure;
        this.strategies = strategies;
        this.jobs = jobs;
        this.workers = workers;
    }

    @GetMapping("/queue")
    public QueueView queue() {
        return new QueueView(backpressure.getDepth(), backpressure.getThreshold(),
                backpressure.isActive(), backpressure.getRejectedTotal());
    }

    @GetMapping("/strategies")
    public StrategiesView strategies() {
        return new StrategiesView(strategies.activeName(),
                strategies.all().entrySet().stream()
                        .map(e -> new StrategyInfo(e.getKey(), e.getValue()))
                        .toList());
    }

    @PostMapping("/strategies/active")
    public Map<String, String> switchStrategy(@RequestBody ActiveStrategyRequest req) {
        if (req.name() == null || !strategies.known(req.name())) {
            throw new ApiExceptions.BadRequestException("Unknown strategy: '" + req.name() + "'");
        }
        return Map.of("active", strategies.switchTo(req.name()));
    }

    @GetMapping("/summary")
    public SummaryView summary() {
        QueueSummary queue = new QueueSummary(backpressure.getDepth(), backpressure.getThreshold(),
                backpressure.isActive());
        Map<String, Long> jobCounts = new LinkedHashMap<>();
        for (String status : new String[]{"QUEUED", "ASSIGNED", "RUNNING", "COMPLETED",
                "FAILED", "CANCELLED"}) {
            jobCounts.put(status, jobs.countByStatus(status));
        }
        jobCounts.put("REJECTED", backpressure.getRejectedTotal());
        Map<String, Long> workerCounts = new LinkedHashMap<>();
        for (String health : new String[]{"HEALTHY", "SUSPECTED", "FAILED"}) {
            workerCounts.put(health, workers.countByHealth(health));
        }
        return new SummaryView(queue, jobCounts, workerCounts, strategies.activeName());
    }
}
