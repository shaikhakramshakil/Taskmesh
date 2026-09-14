import { useState } from 'react';
import { ApiError, api, resultAvg, resultP50, resultP95, type SimulationReport } from '../api.ts';

const ALL_STRATEGIES = ['fifo', 'priority', 'fair', 'resource', 'deadline'];

interface PresetConfig {
  name: string;
  jobs: number;
  seed: number;
  arrivalRate: number;
  workers: string;
}

const PRESETS: PresetConfig[] = [
  {
    name: 'Standard Benchmark (2,000 jobs)',
    jobs: 2000,
    seed: 42,
    arrivalRate: 0.22,
    workers: '8:16384x2,4:8192x4,2:4096x2'
  },
  {
    name: 'Heavy Overload (5,000 jobs)',
    jobs: 5000,
    seed: 99,
    arrivalRate: 0.35,
    workers: '8:16384x2,4:8192x4,2:4096x2'
  },
  {
    name: 'Starvation Stress (3,000 jobs)',
    jobs: 3000,
    seed: 77,
    arrivalRate: 0.30,
    workers: '4:8192x4,2:4096x2'
  },
  {
    name: 'Fast Test (500 jobs)',
    jobs: 500,
    seed: 123,
    arrivalRate: 0.20,
    workers: '4:8192x2,2:4096x2'
  }
];

interface SimulatorViewProps {
  onNotify?: (message: string, type?: 'info' | 'error' | 'warn') => void;
}

export default function SimulatorView({ onNotify }: SimulatorViewProps) {
  const [jobs, setJobs] = useState(2000);
  const [seed, setSeed] = useState(42);
  const [arrivalRate, setArrivalRate] = useState(0.22);
  const [workersSpec, setWorkersSpec] = useState('8:16384x2,4:8192x4,2:4096x2');
  const [strategies, setStrategies] = useState<string[]>([...ALL_STRATEGIES]);
  const [report, setReport] = useState<SimulationReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggleStrategy(name: string) {
    setStrategies((prev) =>
      prev.includes(name) ? prev.filter((s) => s !== name) : [...prev, name]
    );
  }

  function selectAllStrategies() {
    setStrategies([...ALL_STRATEGIES]);
  }

  function applyPreset(p: PresetConfig) {
    setJobs(p.jobs);
    setSeed(p.seed);
    setArrivalRate(p.arrivalRate);
    setWorkersSpec(p.workers);
    if (onNotify) onNotify(`Loaded preset: ${p.name}`, 'info');
  }

  async function onRun(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const data = await api.simulate({
        jobs,
        seed,
        arrivalRate,
        strategies,
        workers: workersSpec
      });
      setReport(data);
      if (onNotify) onNotify(`Simulation finished for ${strategies.length} strategies`, 'info');
    } catch (e) {
      const msg =
        e instanceof ApiError
          ? `${e.code}: ${e.message}`
          : e instanceof Error
          ? e.message
          : 'Simulation failed';
      setError(msg);
      if (onNotify) onNotify(msg, 'error');
    } finally {
      setLoading(false);
    }
  }

  const maxP50 = Math.max(
    1,
    ...(report?.results ?? []).flatMap((r) => r.byPriority.map((b) => b.p50Ticks))
  );

  function starvLabel(ratio: number): 'Low' | 'Medium' | 'High' {
    if (ratio > 4) return 'High';
    if (ratio > 2) return 'Medium';
    return 'Low';
  }

  const bestAvg = report ? Math.min(...report.results.map((r) => resultAvg(r))) : 0;
  const bestP95 = report ? Math.min(...report.results.map((r) => resultP95(r))) : 0;
  const bestThr = report ? Math.max(...report.results.map((r) => r.throughputPerTick)) : 0;
  const bestMissed = report ? Math.min(...report.results.map((r) => r.deadlineMissed)) : 0;

  return (
    <div>
      {/* Simulation Configuration Card */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">Scheduling Simulator</h2>
            <span className="card-subtitle">
              Compare all five placement strategies on identical, reproducible workloads
            </span>
          </div>
        </div>

        {/* Benchmark Presets */}
        <div className="presets-strip">
          <span className="presets-title">Presets:</span>
          {PRESETS.map((p) => (
            <button
              key={p.name}
              type="button"
              className="preset-chip"
              onClick={() => applyPreset(p)}
            >
              {p.name}
            </button>
          ))}
        </div>

        <form onSubmit={onRun}>
          <div className="simulator-config-grid">
            <div className="form-field">
              <label className="field-label">
                Jobs Count
                <span className="field-hint">Max 20,000</span>
              </label>
              <input
                className="text-input"
                type="number"
                min={1}
                max={20000}
                value={jobs}
                onChange={(e) => setJobs(Number(e.target.value))}
              />
            </div>

            <div className="form-field">
              <label className="field-label">
                Random Seed
                <span className="field-hint">Fixed workload generator</span>
              </label>
              <input
                className="text-input"
                type="number"
                value={seed}
                onChange={(e) => setSeed(Number(e.target.value))}
              />
            </div>

            <div className="form-field">
              <label className="field-label">
                Arrival Rate
                <span className="field-hint">Jobs per tick (Poisson)</span>
              </label>
              <input
                className="text-input"
                type="number"
                step="0.01"
                min={0}
                value={arrivalRate}
                onChange={(e) => setArrivalRate(Number(e.target.value))}
              />
            </div>

            <div className="form-field" style={{ gridColumn: 'span 2' }}>
              <label className="field-label">
                Workers Specification
                <span className="field-hint">cores:memoryxcount syntax</span>
              </label>
              <input
                className="text-input"
                value={workersSpec}
                onChange={(e) => setWorkersSpec(e.target.value)}
                placeholder="8:16384x2,4:8192x4,2:4096x2"
              />
            </div>
          </div>

          <div style={{ marginTop: '14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span className="field-label">Strategies to Compare:</span>
              <button
                type="button"
                className="button-app-sm"
                style={{ padding: '2px 8px', fontSize: '11px' }}
                onClick={selectAllStrategies}
              >
                Select All
              </button>
            </div>

            <div className="strategies-checkbox-strip">
              {ALL_STRATEGIES.map((s) => {
                const checked = strategies.includes(s);
                return (
                  <label
                    key={s}
                    className={`strategy-check-pill ${checked ? 'checked' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleStrategy(s)}
                    />
                    <span>{checked ? '✓ ' : '+ '}{s}</span>
                  </label>
                );
              })}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '16px' }}>
            <button
              className="button-primary"
              type="submit"
              disabled={loading || strategies.length === 0}
            >
              {loading ? 'Running Benchmark…' : 'Run Comparison'}
            </button>
            <span className="field-hint">
              Evaluating {strategies.length} strategies across {jobs.toLocaleString()} simulated jobs
            </span>
          </div>
        </form>

        {loading && (
          <div className="notice-box info" style={{ marginTop: '14px' }}>
            Running deterministic multi-strategy simulation. Please wait…
          </div>
        )}

        {error && (
          <div className="notice-box error" style={{ marginTop: '14px' }}>
            {error}
          </div>
        )}
      </section>

      {/* Comparison Results */}
      {report && (
        <>
          <section className="feature-card">
            <div className="card-header">
              <div className="card-title-group">
                <h2 className="card-title">Benchmark Results</h2>
                <span className="card-subtitle">
                  Waits in discrete simulation ticks · {report.results[0]?.completed ?? 0} jobs processed per strategy
                </span>
              </div>
            </div>

            <div className="table-wrap">
              <table className="geist-table">
                <thead>
                  <tr>
                    <th>Strategy</th>
                    <th className="numeric">Avg Wait</th>
                    <th className="numeric">p95 Wait</th>
                    <th className="numeric">Throughput</th>
                    <th>Starvation Ratio</th>
                    <th className="numeric">Missed DL</th>
                    <th className="numeric">Makespan</th>
                  </tr>
                </thead>
                <tbody>
                  {report.results.map((r) => {
                    const label = starvLabel(r.starvationRatio);
                    const isBestAvg = resultAvg(r) === bestAvg;
                    const isBestP95 = resultP95(r) === bestP95;
                    const isBestThr = r.throughputPerTick === bestThr;
                    const isBestMissed = r.deadlineMissed === bestMissed;

                    return (
                      <tr key={r.strategy}>
                        <td>
                          <strong style={{ textTransform: 'uppercase', fontFamily: 'var(--font-mono)', fontSize: '13px' }}>
                            {r.strategy}
                          </strong>
                        </td>

                        <td className="numeric">
                          <span className={isBestAvg ? 'best-val' : ''}>
                            {resultAvg(r).toFixed(0)} ticks
                          </span>
                        </td>

                        <td className="numeric">
                          <span className={isBestP95 ? 'best-val' : ''}>
                            {resultP95(r).toFixed(0)} ticks
                          </span>
                        </td>

                        <td className="numeric">
                          <span className={isBestThr ? 'best-val' : ''}>
                            {(r.throughputPerTick * 1000).toFixed(1)} / 1k
                          </span>
                        </td>

                        <td>
                          <span className={`starv-pill starv-${label}`}>
                            {label} ({r.starvationRatio.toFixed(1)}×)
                          </span>
                        </td>

                        <td className="numeric">
                          <span className={isBestMissed ? 'best-val' : ''}>
                            {r.deadlineMissed}
                          </span>
                        </td>

                        <td className="numeric mono-cell">
                          {r.makespanTicks} ticks
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div style={{ marginTop: '12px', display: 'flex', gap: '20px', flexWrap: 'wrap', fontSize: '12.5px', color: 'var(--mute)' }}>
              <span>
                <strong style={{ color: 'var(--ink)' }}>Starvation Metric:</strong> Median wait of priority ≤ 3 jobs divided by overall median wait.
              </span>
              <span>
                <strong style={{ color: 'var(--ink)' }}>Inverted black pill:</strong> Best metric in column.
              </span>
            </div>
          </section>

          {/* Per-priority Latency Breakdown */}
          <section className="feature-card">
            <div className="card-header">
              <div className="card-title-group">
                <h2 className="card-title">Per-Priority Latency Breakdown</h2>
                <span className="card-subtitle">
                  Median (p50) wait ticks by priority class. Demonstrates how strict priority starves low-priority workloads.
                </span>
              </div>
            </div>

            <div className="prio-latency-grid">
              {report.results.map((r) => (
                <div className="prio-card" key={r.strategy}>
                  <div className="prio-card-header">
                    <span className="prio-card-title">{r.strategy}</span>
                    <span className="field-hint">
                      p50: <b>{resultP50(r).toFixed(1)}</b>
                    </span>
                  </div>

                  {[...r.byPriority]
                    .sort((a, b) => a.priority - b.priority)
                    .map((b) => {
                      const pct = Math.min(100, Math.round((b.p50Ticks / maxP50) * 100));
                      return (
                        <div className="prio-bar-row" key={b.priority}>
                          <span className="prio-bar-tag">P{b.priority}</span>
                          <div className="prio-bar-track" title={`p50: ${b.p50Ticks}, p95: ${b.p95Ticks}, count: ${b.count}`}>
                            <div
                              className="prio-bar-fill"
                              style={{
                                width: `${pct}%`,
                                background: b.priority <= 3 && r.strategy === 'priority' ? 'var(--ink)' : '#8c8c8c'
                              }}
                            />
                          </div>
                          <span style={{ fontSize: '11px', textAlign: 'right', color: 'var(--mute)' }}>
                            {b.p50Ticks.toFixed(0)}t <span style={{ fontSize: '10px' }}>(n={b.count})</span>
                          </span>
                        </div>
                      );
                    })}
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
