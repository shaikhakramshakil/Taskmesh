package io.taskmesh.sim;

/** Knobs for the synthetic workload. {@link #defaults()} targets ~75% CPU load. */
public record WorkloadConfig(
        long seed,
        int jobCount,
        double arrivalRatePerTick,
        int minServiceTicks,
        int maxServiceTicks,
        double deadlineFraction,
        int deadlineSlackMinMult,
        int deadlineSlackMaxMult) {

    public static WorkloadConfig defaults() {
        return new WorkloadConfig(42, 2000, 0.22, 20, 100, 0.6, 3, 8);
    }
}