# TaskMesh API & Integration Contract (v1)

Base URL: `http://localhost:8080`. All bodies are JSON. Instants are ISO-8601 UTC.
Error shape: `{"error": "<CODE>", "message": "<human>"}`.

## Conventions

- Priority: integer 1–10, higher = more urgent.
- CPU: whole cores. Memory: megabytes.
- IDs: server-generated UUID strings, except workers may propose an ID at registration.

## Jobs

### POST /api/v1/jobs — submit
Request: `{"name": string, "priority": 1-10, "cpu": int>=1, "memoryMb": int>=1,
"deadline": instant|null, "metadata": string|null, "maxAttempts": int|null (default 3)}`

- `201` → the stored job view (see below).
- `429 {"error":"BACKPRESSURE", ...}` when the queue is over threshold and `priority < 5`.
- `400` on validation failure.

### GET /api/v1/jobs?status=&limit= — list, newest first
`status` is one of `QUEUED ASSIGNED RUNNING COMPLETED FAILED CANCELLED REJECTED`
(all uppercase). `limit` default 50, max 500.

### GET /api/v1/jobs/{id} — one job. `404` when unknown.

### POST /api/v1/jobs/{id}/cancel — cancel
- `QUEUED`/`ASSIGNED` → `CANCELLED` immediately, resources never touched.
- `RUNNING` → flagged; the worker sees the ID in the next heartbeat's `cancelIds`
  and must abort and report outcome `CANCELLED`.
- Terminal jobs → `409`.

Job view:
```json
{
  "id": "uuid", "name": "video-processing", "priority": 8,
  "cpu": 2, "memoryMb": 4096, "deadline": "2026-09-12T16:00:00Z",
  "status": "RUNNING", "workerId": "w1", "attempts": 1, "maxAttempts": 3,
  "createdAt": "...", "startedAt": "...", "finishedAt": "...", "error": null
}
```

## Workers

### POST /api/v1/workers/register — register
Request: `{"id": string|null, "address": string, "totalCpu": int, "totalMemoryMb": long}`
→ `201 {"id": "..."}` (echoes the proposed ID or a generated one).

### POST /api/v1/workers/{id}/heartbeat — heartbeat + assignment poll (every 2s)
Request: `{"availableCpu": int, "availableMemoryMb": long, "runningJobs": int}`
→ `200`:
```json
{
  "health": "HEALTHY",
  "assignments": [
    {"jobId": "uuid", "name": "...", "priority": 8, "cpu": 2,
     "memoryMb": 4096, "deadline": "..."}
  ],
  "cancelIds": ["uuid"]
}
```
`assignments` are jobs the worker must start (each reported exactly once until ACKed
by completion; redelivered after worker failure). `cancelIds` are running jobs to abort.
Unknown worker → `404` (worker must re-register).

### POST /api/v1/workers/{id}/jobs/{jobId}/complete — report outcome
Request: `{"outcome": "COMPLETED"|"FAILED"|"CANCELLED", "error": string|null}` → `200`.
Reporting an unknown or non-owned job → `404`/`409`; server ignores duplicates.

### GET /api/v1/workers — all workers
```json
[{"id":"w1","address":"...","totalCpu":8,"totalMemoryMb":16384,"availableCpu":5,
"availableMemoryMb":8192,"runningJobs":2,"health":"HEALTHY","lastHeartbeat":"..."}]
```
`health` is `HEALTHY`/`SUSPECTED`/`FAILED`.

## Queue & backpressure

### GET /api/v1/queue
`{"depth": 12, "threshold": 500, "backpressureActive": false, "rejectedTotal": 3}`

Backpressure rule (server-enforced): while `depth > threshold`, submissions with
`priority < 5` are rejected with `429 BACKPRESSURE` and counted in `rejectedTotal`;
an ops event is emitted per rejection burst.

## Strategies

### GET /api/v1/strategies
`{"active": "resource", "strategies": [{"name":"fifo","description":"..."}, ...]}`
Names: `fifo priority fair resource deadline` (domain `SchedulingStrategy.name()`).

### POST /api/v1/strategies/active — switch at runtime
Request: `{"name": "fair"}` → `200 {"active":"fair"}`; unknown name → `400`.
The coordinator uses the new strategy from the next dispatch loop.

## Simulator

### POST /api/v1/simulate — compare strategies on a synthetic workload
Request (all optional, defaults shown):
`{"jobs": 2000, "seed": 42, "arrivalRate": 0.22, "strategies": ["fifo","priority","fair","resource","deadline"], "workers": "8:16384x2,4:8192x4,2:4096x2"}`
→ `200` the `SimulationReport` JSON: `{"config": {...}, "results": [...]}` where each
result has `strategy submitted completed avgLatencyTicks p50LatencyTicks p95LatencyTicks
throughputPerTick deadlineMissed starvationRatio makespanTicks byPriority[]`
and each `byPriority` entry has `priority count p50Ticks p95Ticks`.
Server builds it via `SimulationEngine.runAll` (same code as the CLI). Keep runs small
enough to answer in seconds (refuse `jobs > 20000` with `400`).

## Dashboard summary

### GET /api/v1/summary — one call for the dashboard header
```json
{
  "queue": {"depth": 12, "threshold": 500, "backpressureActive": false},
  "jobs": {"QUEUED": 12, "RUNNING": 5, "COMPLETED": 130, "FAILED": 2, "CANCELLED": 1, "REJECTED": 3},
  "workers": {"HEALTHY": 3, "SUSPECTED": 0, "FAILED": 1},
  "activeStrategy": "resource"
}
```

## Metrics (Prometheus)

- `GET /actuator/prometheus` exposes at least:
  `taskmesh_jobs_submitted_total`, `taskmesh_jobs_completed_total{outcome}`,
  `taskmesh_jobs_rejected_backpressure_total`, `taskmesh_jobs_requeued_total`,
  `taskmesh_queue_depth`, `taskmesh_queue_head_age_seconds`,
  `taskmesh_job_queue_seconds` (histogram, submit→start),
  `taskmesh_workers{health}`, `taskmesh_scheduler_cycle_seconds` (histogram),
  `taskmesh_worker_failures_total`.

## Coordinator semantics (server implements)

- Dispatch loop every ~250ms: load `QUEUED` jobs, `orderJobs`, per job `selectWorker`
  against healthy workers' current availability, CAS `QUEUED→ASSIGNED`, reserve
  resources, hand to worker on next heartbeat. Unplaceable jobs stay queued.
- On `complete(COMPLETED)`: free resources, mark `COMPLETED`. On `FAILED`: free,
  `attempts++`, requeue (`QUEUED`) while `attempts < maxAttempts`, else `FAILED`.
- Deadline sweep: `QUEUED`/`ASSIGNED` jobs past `deadline` → `CANCELLED` with
  `error=DEADLINE_MISSED` (deadline strategy avoids them first via EDF; the sweep
  is the backstop). `RUNNING` jobs past deadline keep running (no preemption).
- Heartbeats: worker `SUSPECTED` after 3 missed beats (~6s), `FAILED` after 5 (~10s).
  On `FAILED`: its `ASSIGNED`+`RUNNING` jobs return to `QUEUED` (`attempts++`,
  respecting `maxAttempts`) and an ops event fires.
- All state transitions are conditional updates (CAS) so restarts and retries are safe.

## Worker execution semantics (worker implements)

- Register at startup (stable `id` from config, e.g. hostname-PID), heartbeat every 2s
  with true availability. On `404`, re-register, then resume heartbeats.
- Execute each assignment once: simulated duration
  `500ms + cpu*250ms + memoryMb/512ms`, interruptible for `cancelIds`.
  Failure injection: config `failure-rate` (default 0) randomly reports `FAILED`.
- Track running jobs; never exceed advertised capacity. On shutdown, finish or
  report current jobs (server requeues whatever is left via failure detection).

## Events (Kafka)

- Topic `taskmesh.jobs`: `{"type":"SUBMITTED|ASSIGNED|STARTED|COMPLETED|FAILED|REQUEUED|CANCELLED|REJECTED|DEADLINE_MISSED","jobId":"...","at":"...","detail":"..."}`
- Topic `taskmesh.ops`: `{"type":"BACKPRESSURE_ACTIVE|BACKPRESSURE_CLEARED|WORKER_SUSPECTED|WORKER_FAILED","at":"...","detail":"..."}`
- Kafka/Redis are **required** at runtime (compose provides them) but must be
  `@ConditionalOnProperty`-gated (`taskmesh.kafka.enabled`, `taskmesh.redis.enabled`,
  default true) so tests run without brokers.

## Redis usage

- `taskmesh:queue:depth` counter (fast path for backpressure checks).
- `taskmesh:worker:{id}:heartbeat` timestamps.
- PostgreSQL remains the source of truth; Redis is derived/fast-path state.

## Runtime config

- Server `:8080`. Env: `PORT`, `DB_URL/DB_USER/DB_PASS` (default
  `jdbc:postgresql://localhost:5432/taskmesh`), `REDIS_HOST`, `KAFKA_BOOTSTRAP`
  (default `localhost:9092`), `TASKMESH_QUEUE_THRESHOLD` (default 500),
  `TASKMESH_STRATEGY` (default `resource`), `TASKMESH_DISPATCH_INTERVAL_MS` (250).
- Worker env: `TASKMESH_SERVER_URL` (default `http://localhost:8080`),
  `WORKER_ID`, `WORKER_CPU`, `WORKER_MEM_MB`, `WORKER_FAILURE_RATE`.
- Tests must not need brokers: H2 + disabled kafka/redis via properties.
- Build with `./scripts/mvnw.sh` (pins JDK 21). Module-scoped commands only:
  `./scripts/mvnw.sh -pl taskmesh-server test` (domain/simulator jars resolve from
  the local repo — do NOT use `-am`, it collides with concurrent module builds).
