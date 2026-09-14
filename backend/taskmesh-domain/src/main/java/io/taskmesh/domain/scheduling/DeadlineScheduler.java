package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.Comparator;
import java.util.List;

/**
 * Earliest-deadline-first: the job with the soonest deadline is dispatched next; jobs without
 * a deadline behave as FIFO relative to each other. Workers are chosen least-utilized-first
 * among those that fit, spreading time-critical work across the mesh.
 */
public final class DeadlineScheduler implements SchedulingStrategy {

    @Override
    public String name() {
        return "deadline";
    }

    @Override
    public List<Job> orderJobs(List<Job> jobs) {
        return jobs.stream()
                .sorted(Comparator
                        .comparing((Job j) -> j.deadline() == null
                                ? java.time.Instant.MAX
                                : j.deadline())
                        .thenComparing(Job::submittedAt)
                        .thenComparing(Job::id))
                .toList();
    }

    @Override
    public Worker selectWorker(Job job, List<Worker> workers) {
        Worker best = null;
        double bestUtilization = Double.MAX_VALUE;
        for (Worker w : workers) {
            if (!w.canFit(job)) {
                continue;
            }
            double utilization = w.cpuUtilization() + w.memoryUtilization();
            if (utilization < bestUtilization) {
                bestUtilization = utilization;
                best = w;
            }
        }
        return best;
    }
}