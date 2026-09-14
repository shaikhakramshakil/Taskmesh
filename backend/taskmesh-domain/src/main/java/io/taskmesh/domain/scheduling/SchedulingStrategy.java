package io.taskmesh.domain.scheduling;

import io.taskmesh.domain.model.Job;
import io.taskmesh.domain.model.Worker;

import java.util.List;

/**
 * Pluggable scheduling strategy, per the TRD.
 *
 * <p>Dispatch has two decisions: <em>which job goes next</em> and <em>which worker hosts it</em>.
 * {@link #selectWorker(Job, List)} covers worker selection exactly as specified in the TRD;
 * {@link #orderJobs(List)} covers job ordering, which FIFO-style strategies inherit from the
 * default (submission order). A coordinator dispatches by iterating {@code orderJobs} and placing
 * each job on its {@code selectWorker} result while workers have capacity.
 *
 * <p>Strategies may keep state (e.g. fairness deficits); {@link #onDispatch(Job)} lets them
 * observe actual placements and {@link #reset()} returns them to a clean slate, which the
 * simulator uses to give every strategy the identical workload.
 */
public interface SchedulingStrategy {

    /** Stable name used in reports, metrics, and the API. */
    String name();

    /**
     * Order {@code jobs} for dispatch. Default: FIFO by submission time.
     * Implementations SHOULD return the same elements, just reordered.
     */
    default List<Job> orderJobs(List<Job> jobs) {
        return jobs.stream()
                .sorted(java.util.Comparator
                        .comparing(Job::submittedAt)
                        .thenComparing(Job::id))
                .toList();
    }

    /**
     * Choose a worker for {@code job} from {@code workers}.
     *
     * @return the selected worker, or {@code null} if no worker can host the job right now
     */
    Worker selectWorker(Job job, List<Worker> workers);

    /** Notified when the coordinator actually hands {@code job} to a worker. */
    default void onDispatch(Job job) {
    }

    /** Notified when a dispatched job finishes or leaves the worker. */
    default void onComplete(Job job) {
    }

    /** Drop all internal state; identical to a fresh instance. */
    default void reset() {
    }
}