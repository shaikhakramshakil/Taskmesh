package io.taskmesh.server.web;

import io.taskmesh.domain.scheduling.DeadlineScheduler;
import io.taskmesh.domain.scheduling.FIFOScheduler;
import io.taskmesh.domain.scheduling.FairScheduler;
import io.taskmesh.domain.scheduling.PriorityScheduler;
import io.taskmesh.domain.scheduling.ResourceAwareScheduler;
import io.taskmesh.sim.SimulationEngine;
import io.taskmesh.sim.SimulationReport;
import io.taskmesh.sim.WorkerProfile;
import io.taskmesh.sim.WorkloadConfig;
import io.taskmesh.sim.WorkloadGenerator;
import io.taskmesh.server.web.Dto.SimulateRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulate")
public class SimulateController {

    @PostMapping
    public SimulationReport simulate(@RequestBody(required = false) SimulateRequest req) {
        int jobCount = req != null && req.jobs() != null ? req.jobs() : 2000;
        long seed = req != null && req.seed() != null ? req.seed() : 42L;
        double arrivalRate = req != null && req.arrivalRate() != null ? req.arrivalRate() : 0.22;
        String workersSpec = req != null && req.workers() != null ? req.workers()
                : "8:16384x2,4:8192x4,2:4096x2";
        if (jobCount < 1 || jobCount > 20000) {
            throw new ApiExceptions.BadRequestException(
                    "jobs must be between 1 and 20000, got " + jobCount);
        }
        Map<String, SimulationEngine.StrategyFactory> all = new LinkedHashMap<>();
        all.put("fifo", tick -> new FIFOScheduler());
        all.put("priority", tick -> new PriorityScheduler());
        all.put("fair", tick -> new FairScheduler());
        all.put("resource", tick -> new ResourceAwareScheduler(tick));
        all.put("deadline", tick -> new DeadlineScheduler());

        List<String> wanted = req != null && req.strategies() != null ? req.strategies()
                : List.copyOf(all.keySet());
        Map<String, SimulationEngine.StrategyFactory> selected = new LinkedHashMap<>();
        for (String name : wanted) {
            String key = name == null ? "" : name.strip().toLowerCase();
            if (!all.containsKey(key)) {
                throw new ApiExceptions.BadRequestException("Unknown strategy: '" + name + "'");
            }
            selected.put(key, all.get(key));
        }
        if (selected.isEmpty()) {
            throw new ApiExceptions.BadRequestException("No strategies selected");
        }
        WorkloadConfig defaults = WorkloadConfig.defaults();
        WorkloadConfig config = new WorkloadConfig(seed, jobCount, arrivalRate,
                defaults.minServiceTicks(), defaults.maxServiceTicks(), defaults.deadlineFraction(),
                defaults.deadlineSlackMinMult(), defaults.deadlineSlackMaxMult());
        List<WorkerProfile> profiles;
        try {
            profiles = WorkerProfile.parse(workersSpec);
        } catch (IllegalArgumentException e) {
            throw new ApiExceptions.BadRequestException(e.getMessage());
        }
        try {
            return SimulationEngine.runAll(selected, WorkloadGenerator.generate(config),
                    profiles, config);
        } catch (IllegalArgumentException e) {
            throw new ApiExceptions.BadRequestException(e.getMessage());
        }
    }
}
