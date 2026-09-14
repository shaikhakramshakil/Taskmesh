package io.taskmesh.sim;

import java.util.List;

/**
 * Outcome of one strategy on the shared workload. Latency is queue wait
 * (start tick minus arrival tick); starvation is the low-priority wait RATIO:
 * pooled median wait of priority&lt;=3 jobs divided by the overall median wait.
 * A ratio near 1 means low-priority jobs wait like everyone else; a large ratio
 * means the discipline strands them while others flow.
 */
public record SimulationResult(
        String strategy,
        int submitted,
        int completed,
        double avgLatencyTicks,
        double p50LatencyTicks,
        double p95LatencyTicks,
        double throughputPerTick,
        long deadlineMissed,
        double starvationRatio,
        long makespanTicks,
        List<ClassLatency> byPriority) {

    /** Wait distribution for one priority class, in ticks. */
    public record ClassLatency(int priority, long count, double p50Ticks, double p95Ticks) {
    }

    /** PRD legend: High / Medium / Low starvation. */
    public String starvationLabel() {
        if (starvationRatio > 4.0) {
            return "High";
        }
        if (starvationRatio > 2.0) {
            return "Medium";
        }
        return "Low";
    }
}