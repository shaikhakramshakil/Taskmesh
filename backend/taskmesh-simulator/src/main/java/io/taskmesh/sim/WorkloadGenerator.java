package io.taskmesh.sim;

import io.taskmesh.domain.model.Job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic workload generator: Poisson arrivals, uniform CPU demand, weighted
 * memory demand, uniform priority, uniform service time, and deadline slack as a
 * multiple of service time. Same seed always yields the same job stream.
 */
public final class WorkloadGenerator {

    /** Wall-clock milliseconds each simulation tick represents. */
    public static final long TICK_MS = 100;

    private static final int[] MEM_OPTIONS = {512, 1024, 2048, 4096, 8192};
    private static final double[] MEM_CUM_WEIGHTS = {0.30, 0.60, 0.80, 0.92, 1.0};

    private WorkloadGenerator() {
    }

    public static List<SimJob> generate(WorkloadConfig config) {
        Random random = new Random(config.seed());
        List<SimJob> jobs = new ArrayList<>(config.jobCount());
        double tick = 0;
        for (int i = 0; i < config.jobCount(); i++) {
            tick += -Math.log(1.0 - random.nextDouble()) / config.arrivalRatePerTick();
            long arrival = (long) tick;
            int cpu = 1 + random.nextInt(4);
            long memoryMb = pickMemory(random);
            int priority = 1 + random.nextInt(10);
            int service = config.minServiceTicks()
                    + random.nextInt(config.maxServiceTicks() - config.minServiceTicks() + 1);
            Long deadlineTick = null;
            Instant deadline = null;
            if (random.nextDouble() < config.deadlineFraction()) {
                int mult = config.deadlineSlackMinMult()
                        + random.nextInt(config.deadlineSlackMaxMult() - config.deadlineSlackMinMult() + 1);
                deadlineTick = arrival + (long) service * mult;
                deadline = Instant.EPOCH.plusMillis(deadlineTick * TICK_MS);
            }
            Job job = new Job("job-" + i, "job-" + i, priority, cpu, memoryMb,
                    Instant.EPOCH.plusMillis(arrival * TICK_MS), deadline, null);
            jobs.add(new SimJob(job, arrival, service, deadlineTick));
        }
        return jobs;
    }

    private static long pickMemory(Random random) {
        double roll = random.nextDouble();
        for (int i = 0; i < MEM_OPTIONS.length; i++) {
            if (roll < MEM_CUM_WEIGHTS[i]) {
                return MEM_OPTIONS[i];
            }
        }
        return MEM_OPTIONS[MEM_OPTIONS.length - 1];
    }
}