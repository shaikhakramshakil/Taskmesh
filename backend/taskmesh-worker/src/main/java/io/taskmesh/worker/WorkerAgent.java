package io.taskmesh.worker;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker agent: registers at startup, heartbeats every 2s with true availability, and runs
 * each assigned job once in a bounded pool. Cancelled jobs abort and report CANCELLED;
 * each job reports its outcome exactly once.
 */
@Component
public class WorkerAgent implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WorkerAgent.class);

    private final WorkerProperties props;
    private final ServerClient client;
    private final ExecutorService executor;
    private final ConcurrentMap<String, RunningJob> running = new ConcurrentHashMap<>();
    private final Set<String> started = ConcurrentHashMap.newKeySet();

    private volatile Random random = new Random();
    private volatile String workerId;
    private volatile boolean registered;
    private volatile boolean shutdown;

    public WorkerAgent(WorkerProperties props, ServerClient client) {
        this.props = props;
        this.client = client;
        int pool = Math.max(1, props.getCpu());
        AtomicInteger seq = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(
                pool,
                r -> {
                    Thread t = new Thread(r, "taskmesh-job-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    /** Test seam for failure injection. */
    public void setRandom(Random random) {
        this.random = random;
    }

    /** Simulated execution time: 500ms + cpu*250ms + memoryMb/512ms. */
    public static long computeDurationMs(int cpu, long memoryMb) {
        return 500L + (long) cpu * 250L + memoryMb / 512L;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            register();
        } catch (Exception e) {
            // Server may not be up yet (compose starts in any order); the
            // scheduled heartbeat retries registration until it succeeds.
            log.warn("initial registration failed, will retry on heartbeat", e);
        }
    }

    public synchronized void register() {
        String proposed = props.effectiveId();
        String returned =
                client.register(proposed, props.effectiveAddress(), props.getCpu(), props.getMemoryMb());
        this.workerId = returned == null || returned.isBlank() ? proposed : returned;
        this.registered = true;
        log.info("worker registered as {}", workerId);
    }

    @Scheduled(
            fixedDelayString = "${taskmesh.worker.heartbeat-interval-ms:2000}",
            initialDelayString = "${taskmesh.worker.heartbeat-interval-ms:2000}")
    public void scheduledHeartbeat() {
        heartbeatOnce();
    }

    /** One heartbeat cycle; public for tests. On 404 re-registers, then continues. */
    public synchronized void heartbeatOnce() {
        if (shutdown) {
            return;
        }
        if (!registered) {
            try {
                register();
            } catch (Exception e) {
                log.warn("registration failed, will retry", e);
                return;
            }
        }
        ServerClient.HeartbeatResponse resp;
        try {
            resp = client.heartbeat(workerId, availableCpu(), availableMemoryMb(), running.size());
        } catch (WorkerNotFoundException e) {
            log.info("server forgot worker, re-registering");
            try {
                register();
                resp = client.heartbeat(workerId, availableCpu(), availableMemoryMb(), running.size());
            } catch (Exception e2) {
                log.warn("heartbeat after re-register failed", e2);
                return;
            }
        } catch (Exception e) {
            log.warn("heartbeat failed", e);
            return;
        }
        if (resp == null) {
            return;
        }
        handleCancels(resp.getCancelIds());
        handleAssignments(resp.getAssignments(), resp.getCancelIds());
    }

    private void handleCancels(List<String> cancelIds) {
        if (cancelIds == null) {
            return;
        }
        for (String id : cancelIds) {
            RunningJob job = running.get(id);
            if (job == null) {
                continue;
            }
            job.cancelled.set(true);
            Future<?> f = job.future;
            if (f != null) {
                f.cancel(true);
            }
            // A task cancelled while still queued never runs, so report CANCELLED here.
            // The reported guard keeps this exactly-once: either this path or the task
            // itself reports, and both agree on CANCELLED.
            if (!job.started.get() && job.reported.compareAndSet(false, true)) {
                report(job.jobId, "CANCELLED", "CANCELLED");
            }
        }
    }

    private void handleAssignments(
            List<ServerClient.JobAssignment> assignments, List<String> cancelIds) {
        if (assignments == null) {
            return;
        }
        for (ServerClient.JobAssignment a : assignments) {
            if (a == null || a.getJobId() == null) {
                continue;
            }
            if (started.contains(a.getJobId())) {
                continue;
            }
            if (cancelIds != null && cancelIds.contains(a.getJobId())) {
                continue;
            }
            // Never oversubscribe: only start what currently fits.
            if (usedCpu() + a.getCpu() > props.getCpu()
                    || usedMemoryMb() + a.getMemoryMb() > props.getMemoryMb()) {
                log.debug("deferring {}: insufficient capacity", a.getJobId());
                continue;
            }
            started.add(a.getJobId());
            RunningJob job = new RunningJob(a.getJobId(), a.getCpu(), a.getMemoryMb());
            running.put(job.jobId, job);
            job.future = executor.submit(() -> execute(job, a));
        }
    }

    private void execute(RunningJob job, ServerClient.JobAssignment assignment) {
        job.started.set(true);
        long totalMs = computeDurationMs(assignment.getCpu(), assignment.getMemoryMb());
        long end = System.currentTimeMillis() + totalMs;
        boolean aborted = job.cancelled.get();
        while (!aborted) {
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                Thread.sleep(Math.min(50, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                aborted = true;
                break;
            }
            if (job.cancelled.get() || Thread.currentThread().isInterrupted()) {
                aborted = true;
            }
        }
        String outcome;
        String error = null;
        if (aborted || job.cancelled.get()) {
            outcome = "CANCELLED";
            error = "CANCELLED";
        } else if (random.nextDouble() < props.getFailureRate()) {
            outcome = "FAILED";
            error = "INJECTED_FAILURE";
        } else {
            outcome = "COMPLETED";
        }
        if (job.reported.compareAndSet(false, true)) {
            report(job.jobId, outcome, error);
        } else {
            running.remove(job.jobId);
        }
    }

    private void report(String jobId, String outcome, String error) {
        try {
            client.complete(workerId, jobId, outcome, error);
        } catch (Exception e) {
            // Server requeues whatever is left via missed heartbeats; duplicates are ignored
            // server-side, so one attempt keeps the exactly-once guarantee.
            log.warn("completion report failed for {}", jobId, e);
        } finally {
            running.remove(jobId);
        }
    }

    public int availableCpu() {
        return Math.max(0, props.getCpu() - usedCpu());
    }

    public long availableMemoryMb() {
        return Math.max(0, props.getMemoryMb() - usedMemoryMb());
    }

    private int usedCpu() {
        int sum = 0;
        for (RunningJob job : running.values()) {
            sum += job.cpu;
        }
        return sum;
    }

    private long usedMemoryMb() {
        long sum = 0;
        for (RunningJob job : running.values()) {
            sum += job.memoryMb;
        }
        return sum;
    }

    /**
     * Graceful shutdown: stop polling; running jobs are interrupted and the server requeues
     * whatever is left via missed heartbeats. No deregistration call needed.
     */
    @PreDestroy
    public void close() {
        shutdown = true;
        executor.shutdownNow();
    }

    private static class RunningJob {
        final String jobId;
        final int cpu;
        final long memoryMb;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean reported = new AtomicBoolean();
        volatile Future<?> future;

        RunningJob(String jobId, int cpu, long memoryMb) {
            this.jobId = jobId;
            this.cpu = cpu;
            this.memoryMb = memoryMb;
        }
    }
}
