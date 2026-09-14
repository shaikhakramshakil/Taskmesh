package io.taskmesh.server.service;

import io.taskmesh.server.config.TaskmeshProperties;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.web.ApiExceptions;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class BackpressureManager {
    private final JobRepository jobs;
    private final RedisState redis;
    private final EventPort events;
    private final TaskmeshMetrics metrics;
    private final TaskmeshProperties props;
    private final AtomicLong rejectedTotal = new AtomicLong();
    private final AtomicBoolean active = new AtomicBoolean(false);

    public BackpressureManager(JobRepository jobs, RedisState redis, EventPort events,
                               @Lazy TaskmeshMetrics metrics, TaskmeshProperties props) {
        this.jobs = jobs;
        this.redis = redis;
        this.events = events;
        this.metrics = metrics;
        this.props = props;
    }

    public long getDepth() {
        return redis.depth().orElseGet(() -> jobs.countByStatus("QUEUED"));
    }

    public int getThreshold() {
        return props.getQueueThreshold();
    }

    public long getRejectedTotal() {
        return rejectedTotal.get();
    }

    public boolean isActive() {
        return updateActive();
    }

    /** Reject low-priority submissions while the queue is over threshold. */
    public void check(int priority) {
        if (updateActive() && priority < 5) {
            rejectedTotal.incrementAndGet();
            metrics.rejectedBackpressure();
            throw new ApiExceptions.BackpressureException(
                    "Queue depth " + getDepth() + " exceeds threshold " + getThreshold());
        }
    }

    /** Recompute the active flag, emitting ops events on transitions. */
    public boolean updateActive() {
        boolean nowActive = getDepth() > getThreshold();
        boolean wasActive = active.getAndSet(nowActive);
        if (nowActive && !wasActive) {
            events.opsEvent("BACKPRESSURE_ACTIVE",
                    "depth " + getDepth() + " > threshold " + getThreshold());
        } else if (!nowActive && wasActive) {
            events.opsEvent("BACKPRESSURE_CLEARED", "depth " + getDepth());
        }
        return nowActive;
    }

    /** Mirror the source-of-truth depth into Redis after mutations. */
    public void refresh() {
        long depth = jobs.countByStatus("QUEUED");
        redis.syncDepth(depth);
        updateActive();
    }
}
