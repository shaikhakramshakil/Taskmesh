package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.List;

/**
 * Strict FIFO: jobs are dispatched in submission order regardless of priority; the first
 * worker in registration order that can fit the job takes it. The baseline strategy.
 */
public final class FIFOScheduler implements SchedulingStrategy {

    @Override
    public String name() {
        return "fifo";
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