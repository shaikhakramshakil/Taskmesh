package io.taskmesh.sim;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;
import io.taskmesh.domain.model.WorkerHealth;
import io.taskmesh.domain.scheduling.SchedulingStrategy;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Discrete-tick simulator. Each tick: admit arrivals, complete finished jobs and release
 * their resources, then dispatch through the strategy until no queued job fits anywhere.
 * One shared workload per {@link #runAll} call, so strategies face identical conditions.
 */
public final class SimulationEngine {

    /** Builds a strategy bound to the run's tick clock (for deadline/age-aware ordering). */
    public interface StrategyFactory {
        SchedulingStrategy create(LongSupplier tickMillis);
    }

    private SimulationEngine() {
    }

    public static SimulationReport runAll(
            Map<String, StrategyFactory> strategies,
            List<SimJob> workload,
            List<WorkerProfile> profiles,
            WorkloadConfig config) {
        List<SimulationResult> results = new ArrayList<>();
        for (var entry : strategies.entrySet()) {
            results.add(run(entry.getKey(), entry.getValue(), workload, profiles, config));
        }
        return new SimulationReport(config, results);
    }

    static SimulationResult run(
            String name,
            StrategyFactory factory,
            List<SimJob> workload,
            List<WorkerProfile> profiles,
            WorkloadConfig config) {
        AtomicLong tick = new AtomicLong(0);
        SchedulingStrategy strategy = factory.create(() -> tick.get() * WorkloadGenerator.TICK_MS);

        List<SimWorker> workers = new ArrayList<>();
        int workerId = 0;
        for (WorkerProfile profile : profiles) {
            for (int i = 0; i < profile.count(); i++) {
                workers.add(new SimWorker("w" + (workerId++), profile.cpu(), profile.memoryMb()));
            }
        }
        Map<String, SimJob> byId = new HashMap<>();
        for (SimJob simJob : workload) {
            byId.put(simJob.job().id(), simJob);
        }

        Deque<SimJob> queue = new ArrayDeque<>();
        PriorityQueue<Running> running = new PriorityQueue<>(Comparator.comparingLong(Running::completeAt));
        List<Wait> waits = new ArrayList<>(workload.size());
        int arrivals = 0;
        int completed = 0;
        long deadlineMissed = 0;

        while (arrivals < workload.size() || !queue.isEmpty() || !running.isEmpty()) {
            long now = tick.get();
            while (arrivals < workload.size() && workload.get(arrivals).arrivalTick() <= now) {
                queue.add(workload.get(arrivals++));
            }
            while (!running.isEmpty() && running.peek().completeAt() <= now) {
                Running finished = running.poll();
                finished.worker().release(finished.job());
                completed++;
                strategy.onComplete(finished.job().job());
                if (finished.job().hasDeadline() && now > finished.job().deadlineTick()) {
                    deadlineMissed++;
                }
            }
            boolean placed;
            do {
                placed = false;
                List<Job> ordered = strategy.orderJobs(queue.stream().map(SimJob::job).toList());
                List<Worker> snapshots = workers.stream().map(SimWorker::snapshot).toList();
                for (Job job : ordered) {
                    Worker selected = strategy.selectWorker(job, snapshots);
                    if (selected != null) {
                        SimJob simJob = byId.get(job.id());
                        SimWorker real = workers.stream()
                                .filter(w -> w.id.equals(selected.id()))
                                .findFirst()
                                .orElseThrow();
                        real.assign(simJob);
                        waits.add(new Wait(simJob.job().priority(), now - simJob.arrivalTick()));
                        running.add(new Running(simJob, real, now + simJob.serviceTicks()));
                        queue.remove(simJob);
                        strategy.onDispatch(job);
                        placed = true;
                        break;
                    }
                }
            } while (placed);
            tick.incrementAndGet();
        }

        List<Long> allWaits = waits.stream().map(Wait::ticks).sorted().toList();
        Map<Integer, List<Long>> perClass = new TreeMap<>();
        for (Wait w : waits) {
            perClass.computeIfAbsent(w.priority(), k -> new ArrayList<>()).add(w.ticks());
        }
        List<SimulationResult.ClassLatency> byPriority = new ArrayList<>();
        for (var entry : perClass.entrySet()) {
            List<Long> sorted = entry.getValue().stream().sorted().toList();
            byPriority.add(new SimulationResult.ClassLatency(entry.getKey(), sorted.size(),
                    percentile(sorted, 0.50), percentile(sorted, 0.95)));
        }
        long makespan = Math.max(1, tick.get());
        double median = percentile(allWaits, 0.50);
        // Starvation is relative: the pooled median wait of low-priority jobs (p<=3)
        // divided by the overall median. Reordering raises variance for every strategy,
        // but only a starving discipline makes low-priority jobs wait a MULTIPLE of
        // everyone else. Well-defined at any load, including an idle mesh (ratio ~1).
        List<Long> lowWaits = waits.stream()
                .filter(w -> w.priority() <= 3)
                .map(Wait::ticks)
                .sorted()
                .toList();
        double lowMedian = percentile(lowWaits, 0.50);
        double starvationRatio = lowMedian / Math.max(1.0, median);
        return new SimulationResult(
                name,
                workload.size(),
                completed,
                average(allWaits),
                median,
                percentile(allWaits, 0.95),
                completed / (double) makespan,
                deadlineMissed,
                starvationRatio,
                makespan,
                byPriority);
    }

    private static double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / (double) values.size();
    }

    private static double percentile(List<Long> sorted, double quantile) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        return sorted.get(Math.min(sorted.size() - 1, (int) (quantile * sorted.size())));
    }

    private record Running(SimJob job, SimWorker worker, long completeAt) {
    }

    private record Wait(int priority, long ticks) {
    }

    /** Mutable tick-domain worker; hands immutable snapshots to strategies. */
    private static final class SimWorker {
        private final String id;
        private final int totalCpu;
        private final long totalMemoryMb;
        private int availableCpu;
        private long availableMemoryMb;
        private int runningJobs;

        private SimWorker(String id, int totalCpu, long totalMemoryMb) {
            this.id = id;
            this.totalCpu = totalCpu;
            this.totalMemoryMb = totalMemoryMb;
            this.availableCpu = totalCpu;
            this.availableMemoryMb = totalMemoryMb;
        }

        private Worker snapshot() {
            return new Worker(id, "sim", totalCpu, totalMemoryMb, availableCpu,
                    availableMemoryMb, runningJobs, WorkerHealth.HEALTHY, Instant.EPOCH, Instant.EPOCH);
        }

        private void assign(SimJob simJob) {
            availableCpu -= simJob.job().requiredCpu();
            availableMemoryMb -= simJob.job().requiredMemoryMb();
            runningJobs++;
        }

        private void release(SimJob simJob) {
            availableCpu += simJob.job().requiredCpu();
            availableMemoryMb += simJob.job().requiredMemoryMb();
            runningJobs--;
        }
    }
}