package io.taskmesh.domain.model;

/** Lifecycle of a job. */
public enum JobStatus {
    QUEUED,
    ASSIGNED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REJECTED
}