package io.taskmesh.domain.model;

/** Health lifecycle of a worker, per the PRD failure-recovery model. */
public enum WorkerHealth {
    HEALTHY,
    SUSPECTED,
    FAILED
}