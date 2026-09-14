package io.taskmesh.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A unit of work submitted to the mesh.
 *
 * <p>Along with {@link Worker}, this is the contract every {@code SchedulingStrategy} reasons
 * over. Immutable; state transitions in the server are modelled in a separate state object.
 */
public record Job(
        String id,
        String name,
        int priority,
        int requiredCpu,
        long requiredMemoryMb,
        Instant submittedAt,
        Instant deadline,
        String metadata) {

    /** Drop-in factory mirroring the PRD submission payload. */
    public static Job of(String name, int priority, int requiredCpu, long requiredMemoryMb,
                         Instant deadline, String metadata) {
        return new Job(UUID.randomUUID().toString(), name, priority, requiredCpu,
                requiredMemoryMb, Instant.now(), deadline, metadata);
    }

    /** Remaining time until the deadline; {@code null} when no deadline is set. */
    public Duration timeToDeadline() {
        return deadline == null ? null : Duration.between(Instant.now(), deadline);
    }
}