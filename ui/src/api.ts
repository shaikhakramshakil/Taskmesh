import * as demo from './demo/engine.ts';

export type JobStatus =
  | 'QUEUED'
  | 'ASSIGNED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'REJECTED';

export type WorkerHealth = 'HEALTHY' | 'SUSPECTED' | 'FAILED';

export interface JobView {
  id: string;
  name: string;
  priority: number;
  cpu: number;
  memoryMb: number;
  deadline: string | null;
  status: JobStatus;
  workerId: string | null;
  attempts: number;
  maxAttempts: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
}

export interface SubmitJobRequest {
  name: string;
  priority: number;
  cpu: number;
  memoryMb: number;
  deadline: string | null;
  metadata?: string | null;
  maxAttempts?: number | null;
}

export interface WorkerView {
  id: string;
  address: string;
  totalCpu: number;
  totalMemoryMb: number;
  availableCpu: number;
  availableMemoryMb: number;
  runningJobs: number;
  health: WorkerHealth;
  lastHeartbeat: string;
}

export interface QueueView {
  depth: number;
  threshold: number;
  backpressureActive: boolean;
  rejectedTotal: number;
}

export interface StrategyInfo {
  name: string;
  description: string;
}

export interface StrategiesView {
  active: string;
  strategies: StrategyInfo[];
}

export interface SummaryView {
  queue: { depth: number; threshold: number; backpressureActive: boolean };
  jobs: Partial<Record<JobStatus, number>>;
  workers: Partial<Record<WorkerHealth, number>>;
  activeStrategy: string;
}

export interface ByPriorityEntry {
  priority: number;
  count: number;
  p50Ticks: number;
  p95Ticks: number;
}

export interface SimulationResult {
  strategy: string;
  submitted?: number;
  completed: number;
  avgLatencyTicks: number;
  p50LatencyTicks?: number;
  p50?: number;
  p95LatencyTicks?: number;
  p95?: number;
  throughputPerTick: number;
  deadlineMissed: number;
  starvationRatio: number;
  makespanTicks: number;
  byPriority: ByPriorityEntry[];
}

export interface SimulationReport {
  config: Record<string, unknown>;
  results: SimulationResult[];
}

export interface SimulateRequest {
  jobs?: number;
  seed?: number;
  arrivalRate?: number;
  strategies?: string[];
  workers?: string;
}

export interface ApiErrorBody {
  error: string;
  message: string;
}

export class ApiError extends Error {
  status: number;
  code: string;
  body: string;
  constructor(status: number, code: string, message: string, body: string) {
    super(message);
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) }
  });
  const text = await res.text();
  if (!res.ok) {
    let code = `HTTP_${res.status}`;
    let message = text || res.statusText;
    try {
      const parsed = JSON.parse(text) as ApiErrorBody;
      if (parsed.error) code = parsed.error;
      if (parsed.message) message = parsed.message;
    } catch {
      // keep raw text
    }
    throw new ApiError(res.status, code, message, text);
  }
  if (text.length === 0) return undefined as T;
  return JSON.parse(text) as T;
}

const liveApi = {
  listJobs(status?: string, limit = 50): Promise<JobView[]> {
    const q = new URLSearchParams({ limit: String(limit) });
    if (status) q.set('status', status);
    return request<JobView[]>(`/api/v1/jobs?${q.toString()}`);
  },
  submitJob(body: SubmitJobRequest): Promise<JobView> {
    return request<JobView>('/api/v1/jobs', { method: 'POST', body: JSON.stringify(body) });
  },
  cancelJob(id: string): Promise<JobView> {
    return request<JobView>(`/api/v1/jobs/${id}/cancel`, { method: 'POST' });
  },
  listWorkers(): Promise<WorkerView[]> {
    return request<WorkerView[]>('/api/v1/workers');
  },
  getQueue(): Promise<QueueView> {
    return request<QueueView>('/api/v1/queue');
  },
  getStrategies(): Promise<StrategiesView> {
    return request<StrategiesView>('/api/v1/strategies');
  },
  setActiveStrategy(name: string): Promise<{ active: string }> {
    return request<{ active: string }>('/api/v1/strategies/active', {
      method: 'POST',
      body: JSON.stringify({ name })
    });
  },
  simulate(body: SimulateRequest): Promise<SimulationReport> {
    return request<SimulationReport>('/api/v1/simulate', {
      method: 'POST',
      body: JSON.stringify(body)
    });
  },
  getSummary(): Promise<SummaryView> {
    return request<SummaryView>('/api/v1/summary');
  }
};
export const DEMO_MODE: boolean = import.meta.env.VITE_DEMO === '1';

/**
 * Offline demo backend (Static Space build). Same signatures, scripted
 * in-memory cluster — see ./demo/engine.ts. Production builds use fetch.
 */
const demoApi = {
  listJobs: (status?: string, limit = 50) => Promise.resolve(demo.listJobs(status, limit)),
  submitJob: (body: SubmitJobRequest) => {
    try {
      return Promise.resolve(demo.submitJob(body));
    } catch (e) {
      return Promise.reject(e);
    }
  },
  cancelJob: (id: string) => {
    try {
      return Promise.resolve(demo.cancelJob(id));
    } catch (e) {
      return Promise.reject(e);
    }
  },
  listWorkers: () => Promise.resolve(demo.listWorkers()),
  getQueue: () => Promise.resolve(demo.getQueue()),
  getStrategies: () => Promise.resolve(demo.getStrategies()),
  setActiveStrategy: (name: string) => {
    try {
      return Promise.resolve(demo.setActiveStrategy(name));
    } catch (e) {
      return Promise.reject(e);
    }
  },
  simulate: (body: SimulateRequest) => {
    try {
      return Promise.resolve(demo.simulate(body));
    } catch (e) {
      return Promise.reject(e);
    }
  },
  getSummary: () => Promise.resolve(demo.getSummary()),
};

export const api = DEMO_MODE ? demoApi : liveApi;

export function resultAvg(r: SimulationResult): number {
  return r.avgLatencyTicks;
}

export function resultP50(r: SimulationResult): number {
  return r.p50LatencyTicks ?? r.p50 ?? 0;
}

export function resultP95(r: SimulationResult): number {
  return r.p95LatencyTicks ?? r.p95 ?? 0;
}
