package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.List;

/** Shared worker-placement rules used by strategies that care about fragmentation. */
public final class WorkerFit {

    private WorkerFit() {
    }

    /**
     * Best-fit: the eligible worker the job leaves least resource unused on. Keeps large
     * workers free for large jobs instead of fragmenting them with small ones.
     *
     * @return the selected worker, or {@code null} if none fits
     */
    public static Worker bestFit(Job job, List<Worker> workers) {
        Worker best = null;
        double bestWaste = Double.MAX_VALUE;
        for (Worker w : workers) {
            if (!w.canFit(job)) {
                continue;
            }
            // Normalized resource left unused if the job lands here.
            double cpuWaste = (double) (w.availableCpu() - job.requiredCpu()) / w.totalCpu();
            double memWaste = (double) (w.availableMemoryMb() - job.requiredMemoryMb()) / w.totalMemoryMb();
            double waste = cpuWaste + memWaste;
            if (waste < bestWaste) {
                bestWaste = waste;
                best = w;
            }
        }
        return best;
    }
}