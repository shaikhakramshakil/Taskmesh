import { useCallback, useEffect, useState } from 'react';
import { DEMO_MODE, api, type StrategiesView, type SummaryView } from './api.ts';
import JobsView from './views/JobsView.tsx';
import WorkersView from './views/WorkersView.tsx';
import SimulatorView from './views/SimulatorView.tsx';
import ArchitectureView from './views/ArchitectureView.tsx';

type Tab = 'jobs' | 'workers' | 'simulator' | 'architecture';

interface Toast {
  id: string;
  message: string;
  type: 'info' | 'error' | 'warn';
}

const STRATEGY_EXPLANATIONS: Record<string, string> = {
  resource: 'Composite score (priority + deadline + queue age) for ordering; best-fit placement.',
  fair: 'Equal-quantum deficit round-robin per priority class. Eliminates starvation (1.0× ratio).',
  deadline: 'Earliest-Deadline-First (EDF) onto least-utilized fitting workers. Zero missed deadlines.',
  priority: 'Strict highest-priority first. Fast for VIPs, but starves low-priority jobs (5.2× latency).',
  fifo: 'Submission order baseline onto first-fit available worker.'
};

export default function App() {
  const [tab, setTab] = useState<Tab>('jobs');
  const [summary, setSummary] = useState<SummaryView | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [strategies, setStrategies] = useState<StrategiesView | null>(null);
  const [strategyError, setStrategyError] = useState<string | null>(null);
  const [switching, setSwitching] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((message: string, type: 'info' | 'error' | 'warn' = 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3500);
  }, []);

  const loadSummary = useCallback(async () => {
    try {
      const s = await api.getSummary();
      setSummary(s);
      setSummaryError(null);
    } catch (e) {
      setSummaryError(e instanceof Error ? e.message : 'Failed to connect to coordinator');
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  const loadStrategies = useCallback(async () => {
    try {
      const s = await api.getStrategies();
      setStrategies(s);
      setStrategyError(null);
    } catch (e) {
      setStrategyError(e instanceof Error ? e.message : 'Failed to load strategies');
    }
  }, []);

  useEffect(() => {
    setSummaryLoading(true);
    void loadSummary();
    void loadStrategies();
    const t = setInterval(() => {
      void loadSummary();
      void loadStrategies();
    }, 5000);
    return () => clearInterval(t);
  }, [loadSummary, loadStrategies]);

  async function onSwitchStrategy(name: string) {
    setSwitching(true);
    setStrategyError(null);
    try {
      const res = await api.setActiveStrategy(name);
      setStrategies((prev) => (prev ? { ...prev, active: res.active } : prev));
      setSummary((prev) => (prev ? { ...prev, activeStrategy: res.active } : prev));
      addToast(`Active strategy: ${res.active.toUpperCase()}`, 'info');
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Strategy switch failed';
      setStrategyError(msg);
      addToast(msg, 'error');
    } finally {
      setSwitching(false);
    }
  }

  const activeStrategy = strategies?.active ?? summary?.activeStrategy ?? 'resource';

  const queueDepth = summary?.queue.depth ?? 0;
  const queueThreshold = summary?.queue.threshold ?? 500;
  const queuePct = Math.min(100, Math.round((queueDepth / queueThreshold) * 100));

  const running = summary?.jobs.RUNNING ?? 0;
  const queued = summary?.jobs.QUEUED ?? 0;
  const completed = summary?.jobs.COMPLETED ?? 0;
  const healthy = summary?.workers.HEALTHY ?? 0;
  const totalWorkers = Object.values(summary?.workers ?? {}).reduce((a, b) => a + (b ?? 0), 0);
  const live = !summaryError && !!summary;

  return (
    <div className="app-container">
      {/* Toast Notification Portal */}
      <div className="toast-portal">
        {toasts.map((t) => (
          <div key={t.id} className="toast-item">
            <span>{t.type === 'error' ? '●' : t.type === 'warn' ? '▲' : '✓'}</span>
            <span>{t.message}</span>
          </div>
        ))}
      </div>

      {/* Top Header Navigation */}
      <header className="shell-header">
        <div className="shell-inner">
          <nav className="nav-bar">
            <a
              href="#top"
              className="nav-brand"
              onClick={(e) => {
                e.preventDefault();
                setTab('jobs');
              }}
            >
              <div className="brand-icon-geom">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2L2 22h20L12 2zm0 4.5l6.5 13.5h-13L12 6.5z" />
                </svg>
              </div>
              <div className="brand-title-wrap">
                <span className="brand-name">TaskMesh</span>
                <span className="brand-separator">/</span>
                <span className="brand-spec">SCHEDULER ENGINE</span>
              </div>
            </a>

            <div className="nav-center-group">
              <div
                className={`nav-status-badge ${live ? 'live' : 'down'}`}
                title={summaryError ?? 'Connected to coordinator REST gateway'}
              >
                <span className={`status-dot ${live ? 'pulsing' : 'offline'}`} />
                <span>{live ? 'COORDINATOR ONLINE' : 'DISCONNECTED'}</span>
              </div>
            </div>

            <div className="nav-actions">
              {DEMO_MODE && (
                <span className="tab-badge" title="Scripted in-memory dataset; no backend connected">
                  DEMO DATA
                </span>
              )}
              <button
                className="button-app-sm"
                onClick={() => setTab('architecture')}
              >
                Docs & API
              </button>
              <button
                className="button-app-primary-sm"
                onClick={() => setTab('jobs')}
              >
                + New Job
              </button>
            </div>
          </nav>
        </div>
      </header>

      {/* Main Page Content */}
      <main className="main-content">
        {/* Minimalist Monochrome Hero Band */}
        <section className="hero-band">
          <div className="hero-content">
            <div className="mono-eyebrow">
              <span className="eyebrow-pip" />
              <span>GEIST MONOCHROME · DISTRIBUTED RESOURCE SCHEDULER</span>
            </div>
            <h1 className="hero-title">
              Resource-aware distributed job scheduling.
            </h1>
            <p className="hero-lead">
              Deterministic workload placement across heterogeneous clusters with pluggable
              scheduling strategies, real-time backpressure gates, and mathematically-proven
              starvation prevention.
            </p>
            <div className="hero-cta-group">
              <button
                className="button-primary"
                onClick={() => setTab('simulator')}
              >
                Compare Strategies
              </button>
              <button
                className="button-secondary"
                onClick={() => setTab('jobs')}
              >
                Deploy Workload
              </button>
              <button
                className="button-secondary"
                onClick={() => setTab('architecture')}
              >
                Architecture & cURL
              </button>
            </div>
          </div>
        </section>

        {/* Connection Notice */}
        {summaryLoading && !summary && (
          <div className="notice-box info" style={{ marginBottom: '16px' }}>
            Establishing connection to coordinator cluster on :8080…
          </div>
        )}

        {summaryError && (
          <div className="notice-box error" style={{ marginBottom: '16px' }}>
            <span>Error connecting to coordinator: {summaryError}. Retrying in 5 seconds…</span>
          </div>
        )}

        {/* Real-time Cluster Stat Tiles */}
        <div className="tiles-grid" aria-label="System Metrics">
          <div className="metric-tile">
            <div className="metric-header">
              <span className="metric-label">Queue Depth</span>
              <span className="brand-spec">CAP {queueThreshold}</span>
            </div>
            <div className="metric-value-wrap">
              <span className="metric-value">{queueDepth}</span>
              <span className="metric-sub">/ {queueThreshold}</span>
            </div>
            <div className="tile-progress-bar">
              <div
                className="tile-progress-fill"
                style={{ width: `${queuePct}%` }}
              />
            </div>
          </div>

          <div className="metric-tile">
            <div className="metric-header">
              <span className="metric-label">Running</span>
              <span className="status-badge status-RUNNING" style={{ padding: '0 6px', fontSize: '10px' }}>
                ACTIVE
              </span>
            </div>
            <div className="metric-value-wrap">
              <span className="metric-value">{running}</span>
              <span className="metric-sub">jobs</span>
            </div>
            <div className="metric-footer">Current resource allocation</div>
          </div>

          <div className="metric-tile">
            <div className="metric-header">
              <span className="metric-label">Queued</span>
              <span className="status-badge status-QUEUED" style={{ padding: '0 6px', fontSize: '10px' }}>
                WAITING
              </span>
            </div>
            <div className="metric-value-wrap">
              <span className="metric-value">{queued}</span>
              <span className="metric-sub">pending</span>
            </div>
            <div className="metric-footer">Waiting for worker capacity</div>
          </div>

          <div className="metric-tile">
            <div className="metric-header">
              <span className="metric-label">Completed</span>
              <span className="status-badge status-COMPLETED" style={{ padding: '0 6px', fontSize: '10px' }}>
                DONE
              </span>
            </div>
            <div className="metric-value-wrap">
              <span className="metric-value">{completed}</span>
              <span className="metric-sub">finished</span>
            </div>
            <div className="metric-footer">Processed successfully</div>
          </div>

          <div className="metric-tile">
            <div className="metric-header">
              <span className="metric-label">Worker Nodes</span>
              <span className="status-badge status-COMPLETED" style={{ padding: '0 6px', fontSize: '10px' }}>
                NODES
              </span>
            </div>
            <div className="metric-value-wrap">
              <span className="metric-value">{healthy}</span>
              <span className="metric-sub">/ {totalWorkers} live</span>
            </div>
            <div className="metric-footer">Heartbeat cycle 2.0s</div>
          </div>
        </div>

        {/* Backpressure Banner */}
        {summary?.queue.backpressureActive && (
          <div className="backpressure-banner">
            <div className="icon">!</div>
            <div>
              <strong>Backpressure Active:</strong> Current queue depth ({summary.queue.depth}) exceeds
              threshold limit ({summary.queue.threshold}). Submissions with priority &lt; 5 receive HTTP 429.
              Priority 5+ submissions continue to be processed.
            </div>
          </div>
        )}

        {/* Strategy Control Bar */}
        <div className="strategy-bar-container">
          <div className="strategy-bar-inner">
            <div className="strategy-left">
              <span className="strategy-label">Strategy:</span>
              <div className="strategy-segmented">
                {(strategies?.strategies ?? [
                  { name: 'fifo', description: 'Submission order' },
                  { name: 'priority', description: 'Strict priority' },
                  { name: 'fair', description: 'Deficit round-robin' },
                  { name: 'resource', description: 'Composite best-fit' },
                  { name: 'deadline', description: 'Earliest deadline first' }
                ]).map((s) => {
                  const isCurrent = activeStrategy === s.name;
                  return (
                    <button
                      key={s.name}
                      className={`strategy-btn ${isCurrent ? 'active' : ''}`}
                      disabled={switching || isCurrent}
                      title={s.description}
                      onClick={() => void onSwitchStrategy(s.name)}
                    >
                      {s.name}
                    </button>
                  );
                })}
              </div>
              {switching && <span className="field-hint">Switching…</span>}
            </div>

            <div className="strategy-description">
              {STRATEGY_EXPLANATIONS[activeStrategy] ?? 'Dynamic scheduling strategy'}
            </div>
          </div>
        </div>

        {strategyError && (
          <div className="notice-box error" style={{ marginBottom: '14px' }}>
            <span>{strategyError}</span>
          </div>
        )}

        {/* Navigation Tabs */}
        <nav className="tabs-nav" aria-label="Views">
          <button
            className={`tab-item-btn ${tab === 'jobs' ? 'active' : ''}`}
            onClick={() => setTab('jobs')}
          >
            <span>Jobs Queue</span>
            <span className="tab-badge">{queued + running}</span>
          </button>

          <button
            className={`tab-item-btn ${tab === 'workers' ? 'active' : ''}`}
            onClick={() => setTab('workers')}
          >
            <span>Worker Nodes</span>
            <span className="tab-badge">{totalWorkers}</span>
          </button>

          <button
            className={`tab-item-btn ${tab === 'simulator' ? 'active' : ''}`}
            onClick={() => setTab('simulator')}
          >
            <span>Scheduling Simulator</span>
            <span className="tab-badge" style={{ background: 'var(--ink)', color: 'var(--on-primary)', borderColor: 'var(--ink)' }}>
              BENCHMARK
            </span>
          </button>

          <button
            className={`tab-item-btn ${tab === 'architecture' ? 'active' : ''}`}
            onClick={() => setTab('architecture')}
          >
            <span>Architecture & API</span>
          </button>
        </nav>

        {/* Active Tab View */}
        {tab === 'jobs' && <JobsView onNotify={addToast} />}
        {tab === 'workers' && <WorkersView onNotify={addToast} />}
        {tab === 'simulator' && <SimulatorView onNotify={addToast} />}
        {tab === 'architecture' && <ArchitectureView onNotify={addToast} />}
      </main>

      {/* Vercel Monochrome Footer */}
      <footer className="shell-footer">
        <div className="footer-inner">
          <div className="footer-left">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2L2 22h20L12 2zm0 4.5l6.5 13.5h-13L12 6.5z" />
            </svg>
            <span>TaskMesh v0.1.0 · Vercel Geist Monochrome</span>
          </div>

          <div className="footer-links">
            <button type="button" onClick={() => setTab('simulator')}>
              Benchmark Simulator
            </button>
            <button type="button" onClick={() => setTab('architecture')}>
              API Specification
            </button>
            <a
              href="http://localhost:3001"
              target="_blank"
              rel="noopener noreferrer"
              title="Grafana Observability Dashboard"
            >
              Grafana (:3001)
            </a>
            <a
              href="http://localhost:9090"
              target="_blank"
              rel="noopener noreferrer"
              title="Prometheus Metrics Gateway"
            >
              Prometheus (:9090)
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
