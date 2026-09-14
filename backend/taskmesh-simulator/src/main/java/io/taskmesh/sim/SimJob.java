package io.taskmesh.sim;

import io.taskmesh.domain.model.Job;

/**
 * One job arrival in tick-space: the domain {@link Job} plus its arrival tick, service
 * demand in ticks, and optional deadline tick. The same list is replayed for every
 * strategy so the comparison is experimental, not anecdotal.
 */
public record SimJob(Job job, long arrivalTick, long serviceTicks, Long deadlineTick) {

    public boolean hasDeadline() {
        return deadlineTick != null;
    }
}