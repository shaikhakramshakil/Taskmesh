import {
  ApiError,
  type JobStatus,
  type JobView,
  type QueueView,
  type SimulateRequest,
  type SimulationReport,
  type StrategiesView,
  type SubmitJobRequest,
  type SummaryView,
  type WorkerHealth,
  type WorkerView,
} from '../api.ts';
import { STANDARD_REPORT } from './report.ts';

/**
 * Offline demo backend (VITE_DEMO=1 build only). A scripted in-memory
 * cluster: every read advances the world one tick — queued jobs get
 * claimed, running jobs finish, fresh ones arrive — so all tabs stay
 * consistent without a server. Writes (submit/cancel/strategy) behave
 * like the real API, including the 429 backpressure rule.
 */

const THRESHOLD = 500;
const now = () => new Date().toISOString();
const rid = () =>
  `${Date.now().toString(36)}-${Math.floor(Math.random() * 0xffff).toString(16)}`;

function job(
  name: string,
  priority: number,
  cpu: number,
  memoryMb: number,
  status: JobStatus,
  workerId: string | null,
  ageMin: number,
): JobView {
  const created = new Date(Date.now() - ageMin * 60000).toISOString();
  return {
    id: rid(),
    name,
    priority,
    cpu,
    memoryMb,
    deadline: null,
    status,
    workerId,
    attempts: status === 'QUEUED' ? 0 : 1,
    maxAttempts: 3,
    createdAt: created,
    startedAt: status === 'QUEUED' ? null : created,
    finishedAt: status === 'COMPLETED' || status === 'FAILED' ? now() : null,
    error: status === 'FAILED' ? 'WORKER_FAILED' : null,
  };
}

const jobs: JobView[] = [
  job('video-encode-h265', 8, 4, 4096, 'RUNNING', 'w-01', 6),
  job('llm-embedding-eval', 9, 2, 8192, 'RUNNING', 'w-02', 4),
  job('warehouse-daily-sync', 5, 2, 2048, 'RUNNING', 'w-01', 11),
  job('stripe-webhook-dispatch', 10, 1, 512, 'QUEUED', null, 1),
  job('report-render-q3', 6, 2, 2048, 'QUEUED', null, 3),
  job('thumb-batch-1142', 4, 1, 1024, 'QUEUED', null, 5),
  job('temp-storage-vacuum', 2, 1, 256, 'QUEUED', null, 9),
  job('video-encode-h264', 8, 4, 4096, 'COMPLETED', 'w-02', 42),
  job('invoice-pdf-run', 5, 1, 1024, 'COMPLETED', 'w-01', 55),
  job('gpu-train-dryrun', 7, 8, 16384, 'FAILED', 'w-03', 31),
  job('log-compact-nightly', 3, 1, 512, 'CANCELLED', null, 70),
];

const workers: WorkerView[] = [
  {
    id: 'w-01', address: '10.0.0.11', totalCpu: 8, totalMemoryMb: 16384,
    availableCpu: 2, availableMemoryMb: 4096, runningJobs: 2,
    health: 'HEALTHY', lastHeartbeat: now(),
  },
  {
    id: 'w-02', address: '10.0.0.12', totalCpu: 4, totalMemoryMb: 8192,
    availableCpu: 1, availableMemoryMb: 1024, runningJobs: 1,
    health: 'HEALTHY', lastHeartbeat: now(),
  },
  {
    id: 'w-03', address: '10.0.0.13', totalCpu: 4, totalMemoryMb: 8192,
    availableCpu: 4, availableMemoryMb: 8192, runningJobs: 0,
    health: 'SUSPECTED', lastHeartbeat: new Date(Date.now() - 9000).toISOString(),
  },
];

let active = 'resource';
let tickN = 0;
let rejectedTotal = 0;

const ARRIVALS: Array<[string, number, number, number]> = [
  ['thumb-batch', 4, 1, 1024],
  ['webhook-retry', 10, 1, 512],
  ['etl-incremental', 5, 2, 2048],
  ['transcode-chunk', 7, 2, 2048],
  ['cleanup-shard', 2, 1, 256],
];

/** Advance the scripted world one step. Called on every read. */
export function tick(): void {
  tickN += 1;
  const queued = jobs.filter((j) => j.status === 'QUEUED');
  const running = jobs.filter((j) => j.status === 'RUNNING');
  const w = workers.find((x) => x.health === 'HEALTHY' && x.availableCpu > 0);
  if (queued.length > 0 && w) {
    const next = queued[0];
    next.status = 'RUNNING';
    next.workerId = w.id;
    next.attempts += 1;
    next.startedAt = now();
    w.availableCpu = Math.max(0, w.availableCpu - next.cpu);
    w.availableMemoryMb = Math.max(0, w.availableMemoryMb - next.memoryMb);
    w.runningJobs += 1;
  } else if (running.length > 0) {
    const done = running[0];
    done.status = 'COMPLETED';
    done.finishedAt = now();
    const owner = workers.find((x) => x.id === done.workerId);
    if (owner) {
      owner.availableCpu = Math.min(owner.totalCpu, owner.availableCpu + done.cpu);
      owner.availableMemoryMb = Math.min(owner.totalMemoryMb, owner.availableMemoryMb + done.memoryMb);
      owner.runningJobs = Math.max(0, owner.runningJobs - 1);
    }
  }
  if (tickN % 3 === 0) {
    const [name, priority, cpu, mem] = ARRIVALS[tickN % ARRIVALS.length];
    jobs.unshift(job(`${name}-${tickN}`, priority, cpu, mem, 'QUEUED', null, 0));
  }
  for (const x of workers) {
    if (x.health === 'HEALTHY') x.lastHeartbeat = now();
  }
  if (jobs.length > 60) jobs.length = 60;
}

function counts(): SummaryView {
  const jc: Partial<Record<JobStatus, number>> = {};
  for (const j of jobs) jc[j.status] = (jc[j.status] ?? 0) + 1;
  const wc: Partial<Record<WorkerHealth, number>> = {};
  for (const x of workers) wc[x.health] = (wc[x.health] ?? 0) + 1;
  const depth = jc.QUEUED ?? 0;
  return {
    queue: { depth, threshold: THRESHOLD, backpressureActive: depth > THRESHOLD },
    jobs: jc,
    workers: wc,
    activeStrategy: active,
  };
}

export function getSummary(): SummaryView {
  tick();
  return counts();
}

export function getQueue(): QueueView {
  tick();
  const depth = jobs.filter((j) => j.status === 'QUEUED').length;
  return { depth, threshold: THRESHOLD, backpressureActive: depth > THRESHOLD, rejectedTotal };
}

export function listJobs(status?: string, limit = 50): JobView[] {
  tick();
  const rows = status ? jobs.filter((j) => j.status === status) : [...jobs];
  return rows.slice(0, limit);
}

export function submitJob(body: SubmitJobRequest): JobView {
  const depth = jobs.filter((j) => j.status === 'QUEUED').length;
  if (depth > THRESHOLD && body.priority < 5) {
    rejectedTotal += 1;
    throw new ApiError(429, 'BACKPRESSURE', `Queue depth ${depth} exceeds threshold ${THRESHOLD}`, '');
  }
  const created = job(body.name, body.priority, body.cpu, body.memoryMb, 'QUEUED', null, 0);
  jobs.unshift(created);
  return created;
}

export function cancelJob(id: string): JobView {
  const found = jobs.find((j) => j.id === id);
  if (!found) throw new ApiError(404, 'NOT_FOUND', `Unknown job: ${id}`, '');
  if (found.status === 'QUEUED' || found.status === 'ASSIGNED') found.status = 'CANCELLED';
  return found;
}

export function listWorkers(): WorkerView[] {
  tick();
  return workers.map((w) => ({ ...w }));
}

const DESCRIPTIONS: Record<string, string> = {
  resource: 'Composite score (priority + deadline + queue age) for ordering; best-fit placement.',
  fair: 'Equal-quantum deficit round-robin per priority class. Eliminates starvation (1.0x ratio).',
  deadline: 'Earliest-Deadline-First (EDF) onto least-utilized fitting workers. Zero missed deadlines.',
  priority: 'Strict highest-priority first. Fast for VIPs, but starves low-priority jobs (5.2x latency).',
  fifo: 'Submission order baseline onto first-fit available worker.',
};

export function getStrategies(): StrategiesView {
  return {
    active,
    strategies: Object.entries(DESCRIPTIONS).map(([name, description]) => ({ name, description })),
  };
}

export function setActiveStrategy(name: string): { active: string } {
  if (!(name in DESCRIPTIONS)) throw new ApiError(400, 'BAD_REQUEST', `Unknown strategy: ${name}`, '');
  active = name;
  return { active };
}

export function simulate(body: SimulateRequest): SimulationReport {
  const wanted = body.strategies && body.strategies.length > 0
    ? body.strategies
    : STANDARD_REPORT.results.map((r) => r.strategy);
  for (const name of wanted) {
    if (!STANDARD_REPORT.results.some((r) => r.strategy === name)) {
      throw new ApiError(400, 'BAD_REQUEST', `Unknown strategy: ${name}`, '');
    }
  }
  return {
    config: {
      seed: body.seed ?? 42,
      jobCount: body.jobs ?? 2000,
      arrivalRatePerTick: body.arrivalRate ?? 0.22,
      note: 'Measured run: Standard Benchmark preset (2000 jobs, seed 42). Same numbers for any config in demo mode.',
    },
    results: STANDARD_REPORT.results.filter((r) => wanted.includes(r.strategy)),
  };
}
