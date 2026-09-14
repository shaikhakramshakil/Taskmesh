package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Equal-quantum deficit round-robin: every priority class present earns one credit per
 * round and each dispatch spends one, so the least-served class always jumps the queue
 * and service shares stay equal across classes however skewed the demand. Priority
 * survives only as the tiebreak when classes are caught up (light load), so fair
 * degrades gracefully from priority-ordering to equal shares under pressure — and a
 * low-priority job's wait stays bounded by the rotation instead of growing without limit.
 *
 * <p>Deliberately not weighted by priority: under sustained overload a class's service
 * share must at least match its arrival share or its queue grows forever, which is
 * relative starvation by another name.
 *
 * <p>Stateful; {@link #onDispatch(Job)} spends the credit, {@link #reset()} clears deficits.
 */
public final class FairScheduler implements SchedulingStrategy {

    private final Map<Integer, Double> deficits = new HashMap<>();

    @Override
    public String name() {
        return "fair";
    }

    @Override
    public synchronized List<Job> orderJobs(List<Job> jobs) {
        jobs.stream()
                .map(Job::priority)
                .distinct()
                .forEach(p -> deficits.merge(p, 1.0, Double::sum));
        // Only relative differences matter: rebase to the minimum each round
        // so the credits stay bounded no matter how long the process runs.
        double floor = deficits.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        if (floor != 0.0) {
            deficits.replaceAll((p, d) -> d - floor);
        }
        return jobs.stream()
                .sorted(Comparator
                        .comparingDouble((Job j) -> deficits.getOrDefault(j.priority(), 0.0))
                        .reversed()
                        .thenComparing(Job::priority, Comparator.reverseOrder())
                        .thenComparing(Job::submittedAt)
                        .thenComparing(Job::id))
                .toList();
    }

    @Override
    public Worker selectWorker(Job job, List<Worker> workers) {
        // Best-fit placement: fairness orders jobs, packing decides hosts. First-fit here
        // would fragment large workers with small jobs and strand the big ones.
        return WorkerFit.bestFit(job, workers);
    }

    @Override
    public synchronized void onDispatch(Job job) {
        deficits.compute(job.priority(), (p, d) -> d == null ? -1.0 : d - 1.0);
    }

    @Override
    public synchronized void reset() {
        deficits.clear();
    }
}