package io.taskmesh.sim;

import io.taskmesh.domain.scheduling.FIFOScheduler;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Engine invariants: deterministic replay, full drain of admitted work, and sane metrics.
 */
class SimulationEngineTest {

    private static final Map<String, SimulationEngine.StrategyFactory> ALL = allStrategies();

    private static Map<String, SimulationEngine.StrategyFactory> allStrategies() {
        Map<String, SimulationEngine.StrategyFactory> strategies = new LinkedHashMap<>();
        strategies.put("fifo", tick -> new io.taskmesh.domain.scheduling.FIFOScheduler());
        strategies.put("priority", tick -> new io.taskmesh.domain.scheduling.PriorityScheduler());
        strategies.put("fair", tick -> new io.taskmesh.domain.scheduling.FairScheduler());
        strategies.put("resource", tick -> new io.taskmesh.domain.scheduling.ResourceAwareScheduler(tick));
        strategies.put("deadline", tick -> new io.taskmesh.domain.scheduling.DeadlineScheduler());
        return strategies;
    }

    @Test
    void sameSeedReplaysIdenticalResults() {
        WorkloadConfig config = new WorkloadConfig(7, 300, 0.25, 10, 40, 0.5, 3, 6);
        SimulationReport first = SimulationEngine.runAll(ALL, WorkloadGenerator.generate(config),
                WorkerProfile.defaults(), config);
        SimulationReport second = SimulationEngine.runAll(ALL, WorkloadGenerator.generate(config),
                WorkerProfile.defaults(), config);
        assertEquals(first.results(), second.results());
    }

    @Test
    void everyStrategyDrainsAllAdmittedWork() {
        WorkloadConfig config = new WorkloadConfig(7, 300, 0.25, 10, 40, 0.5, 3, 6);
        SimulationReport report = SimulationEngine.runAll(ALL, WorkloadGenerator.generate(config),
                WorkerProfile.defaults(), config);
        assertEquals(5, report.results().size());
        for (SimulationResult result : report.results()) {
            assertEquals(300, result.completed(),
                    result.strategy() + " must complete every admitted job");
            assertTrue(result.avgLatencyTicks() >= 0);
            assertTrue(result.throughputPerTick() > 0);
            assertNotNull(result.starvationLabel());
        }
    }

    @Test
    void fifoAloneOnTinyWorkloadMatchesHandComputedLatency() {
        // One worker, one 1-CPU job arriving at tick 0 with service 10: zero queue wait.
        WorkloadConfig config = new WorkloadConfig(1, 1, 1.0, 10, 10, 0.0, 3, 6);
        Map<String, SimulationEngine.StrategyFactory> fifo =
                Map.of("fifo", tick -> new FIFOScheduler());
        SimulationReport report = SimulationEngine.runAll(fifo, WorkloadGenerator.generate(config),
                WorkerProfile.parse("4:8192x1"), config);
        SimulationResult result = report.results().get(0);
        assertEquals(1, result.completed());
        assertEquals(0.0, result.avgLatencyTicks());
    }

    @Test
    void fairBoundsLowPriorityWaitsWhereStrictPriorityStarves() {
        WorkloadConfig config = new WorkloadConfig(11, 500, 0.30, 20, 80, 0.5, 3, 6);
        Map<String, SimulationEngine.StrategyFactory> strategies = new LinkedHashMap<>();
        strategies.put("priority",
                tick -> new io.taskmesh.domain.scheduling.PriorityScheduler());
        strategies.put("fair", tick -> new io.taskmesh.domain.scheduling.FairScheduler());
        SimulationReport report = SimulationEngine.runAll(strategies,
                WorkloadGenerator.generate(config), WorkerProfile.defaults(), config);
        double priorityStarvation = report.results().get(0).starvationRatio();
        double fairStarvation = report.results().get(1).starvationRatio();
        assertTrue(fairStarvation < priorityStarvation,
                "fair=" + fairStarvation + " priority=" + priorityStarvation);
        assertTrue(fairStarvation < 2.0, "fair must stay in the Low band, was " + fairStarvation);
    }
}