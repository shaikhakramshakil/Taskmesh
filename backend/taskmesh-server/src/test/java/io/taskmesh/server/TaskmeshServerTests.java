package io.taskmesh.server;

import io.taskmesh.server.model.JobEntity;
import io.taskmesh.server.model.WorkerEntity;
import io.taskmesh.server.repo.JobRepository;
import io.taskmesh.server.repo.WorkerRepository;
import io.taskmesh.server.service.BackpressureManager;
import io.taskmesh.server.service.DeadlineSweep;
import io.taskmesh.server.service.Dispatcher;
import io.taskmesh.server.service.JobService;
import io.taskmesh.server.service.WorkerRegistry;
import io.taskmesh.server.web.ApiExceptions;
import io.taskmesh.server.web.Dto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TaskmeshServerTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JobService jobs;
    @Autowired
    private WorkerRegistry workers;
    @Autowired
    private Dispatcher dispatcher;
    @Autowired
    private DeadlineSweep sweep;
    @Autowired
    private BackpressureManager backpressure;
    @Autowired
    private JobRepository jobRepo;
    @Autowired
    private WorkerRepository workerRepo;

    @AfterEach
    void cleanUp() {
        jobRepo.deleteAll();
        workerRepo.deleteAll();
    }

    private Dto.SubmitJobRequest job(String name, int priority) {
        return new Dto.SubmitJobRequest(name, priority, 1, 512L, null, null, null);
    }

    @Test
    void backpressureRejectsLowPriorityWhenDepthExceedsThreshold() throws Exception {
        // Threshold is 3 in the test profile; high-priority jobs fill the queue past it.
        for (int i = 0; i < 4; i++) {
            jobs.submit(job("bulk-" + i, 8));
        }
        assertTrue(backpressure.getDepth() > backpressure.getThreshold());

        assertThrows(ApiExceptions.BackpressureException.class,
                () -> jobs.submit(job("low", 2)));
        assertEquals(1, backpressure.getRejectedTotal());

        mvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"low-http","priority":2,"cpu":1,"memoryMb":512}"""))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("BACKPRESSURE"));
        assertEquals(2, backpressure.getRejectedTotal());

        // High-priority jobs still pass while backpressure is active.
        mvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"high-http","priority":9,"cpu":1,"memoryMb":512}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelQueuedJobWorksAndMissingJob404s() throws Exception {
        Dto.JobView created = jobs.submit(job("cancel-me", 7));

        mvc.perform(post("/api/v1/jobs/" + created.id() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/v1/jobs/does-not-exist/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // Cancelling a terminal job is a conflict.
        mvc.perform(post("/api/v1/jobs/" + created.id() + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void heartbeatFailureRequeuesAssignedJob() {
        workers.register("w-fail", "10.0.0.1", 8, 16384L);
        Dto.JobView created = jobs.submit(job("doomed", 8));

        assertEquals(1, dispatcher.dispatchOnce());
        JobEntity assigned = jobRepo.findById(created.id()).orElseThrow();
        assertEquals("ASSIGNED", assigned.getStatus());
        assertEquals("w-fail", assigned.getWorkerId());

        Dto.HeartbeatResponse beat =
                workers.heartbeat("w-fail", 7, 15872L, 1);
        assertEquals("HEALTHY", beat.health());
        assertEquals(1, beat.assignments().size());
        assertEquals(created.id(), beat.assignments().get(0).jobId());
        assertEquals("RUNNING",
                jobRepo.findById(created.id()).orElseThrow().getStatus());

        // Three missed beats -> SUSPECTED.
        WorkerEntity w = workerRepo.findById("w-fail").orElseThrow();
        w.setLastHeartbeat(Instant.now().minus(7, ChronoUnit.SECONDS));
        workerRepo.save(w);
        workers.checkHeartbeats();
        assertEquals("SUSPECTED", workerRepo.findById("w-fail").orElseThrow().getHealth());
        assertEquals("RUNNING", jobRepo.findById(created.id()).orElseThrow().getStatus());

        // Five missed beats -> FAILED, job returns to QUEUED with attempts bumped.
        w = workerRepo.findById("w-fail").orElseThrow();
        w.setLastHeartbeat(Instant.now().minus(11, ChronoUnit.SECONDS));
        workerRepo.save(w);
        workers.checkHeartbeats();
        assertEquals("FAILED", workerRepo.findById("w-fail").orElseThrow().getHealth());
        JobEntity requeued = jobRepo.findById(created.id()).orElseThrow();
        assertEquals("QUEUED", requeued.getStatus());
        assertEquals(2, requeued.getAttempts());
    }

    @Test
    void dispatcherAssignsFittingJobEndToEnd() {
        workers.register("w-fit", "10.0.0.2", 4, 8192L);
        Dto.JobView created = jobs.submit(job("fit-me", 6));

        assertEquals(1, dispatcher.dispatchOnce());

        JobEntity e = jobRepo.findById(created.id()).orElseThrow();
        assertEquals("ASSIGNED", e.getStatus());
        assertEquals("w-fit", e.getWorkerId());
        WorkerEntity w = workerRepo.findById("w-fit").orElseThrow();
        assertEquals(3, w.getAvailableCpu());
        assertEquals(7680L, w.getAvailableMemoryMb());

        Dto.HeartbeatResponse beat = workers.heartbeat("w-fit", 3, 7680L, 1);
        assertEquals(1, beat.assignments().size());
        assertTrue(beat.cancelIds().isEmpty());

        Dto.JobView done = jobs.complete("w-fit", created.id(), "COMPLETED", null);
        assertEquals("COMPLETED", done.status());
        assertEquals(4, workerRepo.findById("w-fit").orElseThrow().getAvailableCpu());
    }

    @Test
    void deadlineSweepCancelsOverdueQueuedJob() {
        Dto.SubmitJobRequest overdue = new Dto.SubmitJobRequest(
                "overdue", 9, 1, 512L, Instant.now().minus(1, ChronoUnit.HOURS), null, null);
        Dto.JobView created = jobs.submit(overdue);

        assertEquals(1, sweep.sweepOnce());

        JobEntity e = jobRepo.findById(created.id()).orElseThrow();
        assertEquals("CANCELLED", e.getStatus());
        assertEquals("DEADLINE_MISSED", e.getError());
    }
    @Test
    @Transactional
    void casReserveRefusesOverAllocation() {
        workers.register("w-small", "10.0.0.9", 2, 2048L);
        assertEquals(1, workerRepo.casReserve("w-small", 2, 1024L));
        assertEquals(0, workerRepo.casReserve("w-small", 1, 512L),
                "second reserve must fail: only 0 CPU left committed in the row");
        WorkerEntity w = workerRepo.findById("w-small").orElseThrow();
        assertEquals(0, w.getAvailableCpu());
    }
}
