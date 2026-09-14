# TaskMesh

A resource-aware distributed job scheduling platform: thousands of competing jobs,
limited workers, and an explicit, experimentally-compared answer to
*which job runs on which worker, and when*.

```
                  Client (REST / dashboard)
                      │
                      ▼
                  Job Coordinator ──► Scheduler (pluggable strategy)
                      │                         │
              ┌───────┴───────┐                 ▼
              ▼               ▼        Worker 1 … Worker N
         Job store      Queue depth
        (PostgreSQL)   (Redis fast-path)
              │               │
              └───────┬───────┘
                      ▼
            Events (Kafka) ──► Metrics (Prometheus/Grafana)
```

## Quickstart

```bash
docker compose up -d --build
# UI:        http://localhost:8081
# Grafana:   http://localhost:3001 (admin/admin)
# Prometheus http://localhost:9090
```

Submit a job (PRD payload):

```bash
curl -XPOST localhost:8080/api/v1/jobs -H 'Content-Type: application/json' -d '{
  "name": "video-processing", "priority": 8, "cpu": 2, "memoryMb": 4096,
  "deadline": "2026-09-13T01:00:00Z"
}'
curl localhost:8080/api/v1/summary
```

Local development (no Docker): start Postgres/Redis/Kafka via
`docker compose up -d postgres redis kafka`, then run the jars or
`./scripts/mvnw.sh -pl taskmesh-server test`.

## The killer feature: scheduling simulator

Compare all five strategies on one reproducible workload (same seed ⇒ same jobs):

```bash
scripts/sim-compare.sh --jobs 10000
# or: java -jar backend/taskmesh-simulator/target/taskmesh-simulator-*-exec.jar --help
```

Measured on 2,000 jobs over 8 workers (`8:16384x2,4:8192x4,2:4096x2`), seed 42:

```
Strategy   Completed  Avg latency  p95 latency     Throughput       Starvation   Missed DL
fifo            2000        6935ms       21200ms        216.6/1k     Low ( 1.0x)         111
priority        2000       22781ms      122800ms        208.1/1k    High ( 5.2x)         122
fair            2000        7679ms       35900ms        217.2/1k     Low ( 1.0x)         136
resource        2000        6530ms       21800ms        216.8/1k     Low ( 1.8x)          88
deadline        2000       16506ms       93800ms        211.7/1k     Low ( 1.0x)           0
```

Starvation here is a *ratio*: median wait of priority≤3 jobs ÷ overall median wait.
Strict priority strands low-priority jobs 5.2× longer than everyone else; fair
rotation brings them back to parity (1.0×). EDF never misses a deadline but pays in
average latency; resource-aware best-fit wins average latency. Same engine backs
`POST /api/v1/simulate`, rendered in the dashboard's Simulator tab with per-priority
breakdowns. Details: `docs/api.md`.

## Strategies

`SchedulingStrategy` (`taskmesh-domain`, pure Java, no Spring) keeps the TRD
contract — `Worker selectWorker(Job job, List<Worker> workers)` — plus an ordering
hook and dispatch callbacks:

- **fifo** — submission order, first-fit worker. The baseline.
- **priority** — highest priority first. Fast for VIPs; starves the rest (measured).
- **fair** — equal-quantum deficit round-robin per priority class: least-served class
  jumps the queue, so shares stay equal however skewed the demand. Priority is only
  the tiebreak when classes are caught up. Deliberately *not* weighted: under
  sustained overload a class's service share must match its arrival share or its
  queue grows forever — weighted fair queueing *is* starvation with better PR.
- **resource** (default) — composite score (priority + deadline pressure + queue age)
  for ordering, best-fit for placement: smallest sufficient worker, so a 16 GB job
  never lands on 4 GB free and small jobs don't fragment large workers.
- **deadline** — earliest-deadline-first onto least-utilized fitting workers.

Every strategy ships with unit tests, including the fairness bound
(low-priority dispatch under sustained high-priority pressure).

## Failure recovery & backpressure

- Workers heartbeat every 2s with true availability. 3 missed beats → `SUSPECTED`,
  5 → `FAILED`; `ASSIGNED`+`RUNNING` jobs return to `QUEUED` with `attempts++`
  (honoring `maxAttempts`, default 3). Verified live by killing a loaded worker.
- `GET /api/v1/queue` reports depth vs threshold (default 500). While over
  threshold, `priority < 5` submissions get `429 BACKPRESSURE` (counted, with ops
  events); high-priority work keeps flowing. Verified live with threshold 3.
- Past-deadline queued jobs are cancelled as `DEADLINE_MISSED` (no preemption of
  running jobs); retries requeue with backoff via attempts.

Full REST + event + metrics contract: [`docs/api.md`](docs/api.md).

## Layout

```
backend/taskmesh-domain     model + 5 strategies + unit tests (pure Java)
backend/taskmesh-simulator  workload generator + tick engine + CLI/JSON report
backend/taskmesh-server     coordinator, REST, JPA/Flyway, Redis, Kafka, metrics
backend/taskmesh-worker     worker agent (register → heartbeat → execute → report)
ui                          React+TS dashboard (jobs, workers, simulator, strategies)
deploy                      prometheus.yml, Grafana datasource + dashboard
compose.yaml                postgres 15, redis 7, kafka KRaft, server×1, worker×2,
                            ui, prometheus, grafana
scripts                     mvnw.sh (JDK 21), sim-compare.sh, smoke.sh
```

## Build, test, verify

```bash
./scripts/mvnw.sh -pl taskmesh-domain test      # 15 strategy tests
./scripts/mvnw.sh -pl taskmesh-simulator test   # 4 engine tests (determinism, drain, fairness bound)
./scripts/mvnw.sh -pl taskmesh-server test      # 5 service tests (H2, no brokers)
./scripts/mvnw.sh -pl taskmesh-worker test      # 4 agent tests
cd ui && npm install && npm run build
scripts/smoke.sh                                # live e2e against :8080
```

## Notes & limits

- PostgreSQL 15 (Flyway 11 OSS needs the `flyway-database-postgresql` module;
  PG16+ patch allowlists postdate it — pinned accordingly).
- Kafka/Redis are required at runtime but property-gated off in tests.
- Single coordinator (CAS transitions keep dispatch idempotent); multi-coordinator
  leader election is the natural next step, as is preemption for hard deadlines.
- Simulator ticks are logical (100ms); it compares disciplines, not hardware.
