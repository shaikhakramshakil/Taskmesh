package io.taskmesh.sim;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskmesh.domain.scheduling.DeadlineScheduler;
import io.taskmesh.domain.scheduling.FIFOScheduler;
import io.taskmesh.domain.scheduling.FairScheduler;
import io.taskmesh.domain.scheduling.PriorityScheduler;
import io.taskmesh.domain.scheduling.ResourceAwareScheduler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point. Compares strategies on one generated workload and prints the
 * PRD comparison table (or JSON for the dashboard).
 *
 * <p>Example: {@code java -jar taskmesh-simulator.jar --jobs 10000 --format table}
 */
public final class SimMain {

    private SimMain() {
    }

    public static void main(String[] args) throws Exception {
        WorkloadConfig base = WorkloadConfig.defaults();
        int jobs = base.jobCount();
        long seed = base.seed();
        double arrivalRate = base.arrivalRatePerTick();
        String strategiesArg = "fifo,priority,fair,resource,deadline";
        String workersSpec = "8:16384x2,4:8192x4,2:4096x2";
        String format = "table";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jobs" -> jobs = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--arrival-rate" -> arrivalRate = Double.parseDouble(args[++i]);
                case "--strategies" -> strategiesArg = args[++i];
                case "--workers" -> workersSpec = args[++i];
                case "--format" -> format = args[++i];
                case "--help" -> {
                    printHelp();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        WorkloadConfig config = new WorkloadConfig(seed, jobs, arrivalRate,
                base.minServiceTicks(), base.maxServiceTicks(), base.deadlineFraction(),
                base.deadlineSlackMinMult(), base.deadlineSlackMaxMult());
        List<SimJob> workload = WorkloadGenerator.generate(config);
        List<WorkerProfile> profiles = WorkerProfile.parse(workersSpec);

        Map<String, SimulationEngine.StrategyFactory> all = new LinkedHashMap<>();
        all.put("fifo", tick -> new FIFOScheduler());
        all.put("priority", tick -> new PriorityScheduler());
        all.put("fair", tick -> new FairScheduler());
        all.put("resource", tick -> new ResourceAwareScheduler(tick));
        all.put("deadline", tick -> new DeadlineScheduler());
        Map<String, SimulationEngine.StrategyFactory> selected = new LinkedHashMap<>();
        for (String name : strategiesArg.split(",")) {
            String key = name.strip().toLowerCase();
            if (!all.containsKey(key)) {
                throw new IllegalArgumentException("Unknown strategy: '" + name
                        + "'. Expected one of " + all.keySet());
            }
            selected.put(key, all.get(key));
        }

        SimulationReport report = SimulationEngine.runAll(selected, workload, profiles, config);
        if (format.equals("json")) {
            System.out.println(new ObjectMapper().writeValueAsString(report));
        } else if (format.equals("table")) {
            System.out.print(report.toTable());
        } else {
            throw new IllegalArgumentException("Unknown format: '" + format + "'. Expected table|json.");
        }
    }

    private static void printHelp() {
        System.out.println("""
                TaskMesh scheduling simulator.
                Usage: java -jar taskmesh-simulator.jar [options]
                  --jobs N            jobs in the workload (default 2000)
                  --seed N            RNG seed; same seed = same workload (default 42)
                  --arrival-rate F    Poisson arrivals per tick (default 0.22, ~92% CPU load)
                  --strategies LIST   comma-separated: fifo,priority,fair,resource,deadline
                  --workers SPEC      e.g. "8:16384x2,4:8192x4,2:4096x2"
                  --format table|json human table (default) or JSON for the dashboard
                Starvation = median p<=3 wait divided by overall median wait (ratio).
                """);
    }
}