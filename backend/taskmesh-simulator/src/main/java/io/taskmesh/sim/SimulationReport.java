package io.taskmesh.sim;

import java.util.List;
import java.util.Locale;

/** Side-by-side outcome for every strategy on one workload, plus the PRD comparison table. */
public record SimulationReport(WorkloadConfig config, List<SimulationResult> results) {

    /** Renders the killer-feature table: strategy × latency × throughput × starvation. */
    public String toTable() {
        StringBuilder table = new StringBuilder();
        table.append(String.format(Locale.ROOT,
                "%-10s %9s %12s %12s %14s %16s %11s%n",
                "Strategy", "Completed", "Avg latency", "p95 latency", "Throughput", "Starvation", "Missed DL"));
        for (SimulationResult r : results) {
            table.append(String.format(Locale.ROOT,
                    "%-10s %9d %11.0fms %11.0fms %12.1f/1k %7s (%4.1fx) %11d%n",
                    r.strategy(),
                    r.completed(),
                    r.avgLatencyTicks() * WorkloadGenerator.TICK_MS,
                    r.p95LatencyTicks() * WorkloadGenerator.TICK_MS,
                    r.throughputPerTick() * 1000,
                    r.starvationLabel(),
                    r.starvationRatio(),
                    r.deadlineMissed()));
        }
        return table.toString();
    }
}