package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.Comparator;
import java.util.List;

/**
 * Strict priority: highest priority first, submission order as the tiebreak. Fast for
 * high-priority work but low-priority jobs can starve (the simulator quantifies this).
 * Worker selection is first-fit.
 */
public final class PriorityScheduler implements SchedulingStrategy {

    @Override
    public String name() {
        return "priority";
    }

    @Override
    public List<Job> orderJobs(List<Job> jobs) {
        return jobs.stream()
                .sorted(Comparator
                        .comparing(Job::priority).reversed()
                        .thenComparing(Job::submittedAt)
                        .thenComparing(Job::id))
                .toList();
    }

    @Override
    public Worker selectWorker(Job job, List<Worker> workers) {
        for (Worker w : workers) {
            if (w.canFit(job)) {
                return w;
            }
        }
        return null;
    }
}