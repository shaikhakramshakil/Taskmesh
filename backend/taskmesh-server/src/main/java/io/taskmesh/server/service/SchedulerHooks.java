package io.taskmesh.server.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix = "taskmesh.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulerHooks {
    private final Dispatcher dispatcher;
    private final DeadlineSweep sweep;
    private final WorkerRegistry workers;

    public SchedulerHooks(Dispatcher dispatcher, DeadlineSweep sweep, WorkerRegistry workers) {
        this.dispatcher = dispatcher;
        this.sweep = sweep;
        this.workers = workers;
    }

    @Scheduled(fixedDelayString = "${taskmesh.dispatch-interval-ms:250}")
    public void dispatch() {
        dispatcher.dispatchOnce();
    }

    @Scheduled(fixedDelay = 1000)
    public void sweepDeadlines() {
        sweep.sweepOnce();
    }

    @Scheduled(fixedDelayString = "${taskmesh.heartbeat.check-interval-ms:2000}")
    public void checkHeartbeats() {
        workers.checkHeartbeats();
    }
}
