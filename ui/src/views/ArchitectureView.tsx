import { useState } from 'react';

interface ArchitectureViewProps {
  onNotify?: (message: string, type?: 'info' | 'error' | 'warn') => void;
}

export default function ArchitectureView({ onNotify }: ArchitectureViewProps) {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  function copyCode(text: string, key: string) {
    void navigator.clipboard.writeText(text);
    setCopiedKey(key);
    if (onNotify) onNotify('Copied snippet to clipboard', 'info');
    setTimeout(() => {
      setCopiedKey(null);
    }, 2000);
  }

  const submitCurl = `curl -X POST http://localhost:8080/api/v1/jobs \\
  -H 'Content-Type: application/json' \\
  -d '{
    "name": "video-transcode-4k",
    "priority": 8,
    "cpu": 4,
    "memoryMb": 8192,
    "deadline": "2026-09-14T12:00:00Z"
  }'`;

  const simulateCurl = `curl -X POST http://localhost:8080/api/v1/simulate \\
  -H 'Content-Type: application/json' \\
  -d '{
    "jobs": 2000,
    "seed": 42,
    "arrivalRate": 0.22,
    "strategies": ["fifo", "fair", "resource"],
    "workers": "8:16384x2,4:8192x4,2:4096x2"
  }'`;

  const strategyCurl = `curl -X POST http://localhost:8080/api/v1/strategies/active \\
  -H 'Content-Type: application/json' \\
  -d '{"name": "fair"}'`;

  return (
    <div>
      {/* Node-graph Architecture section */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">System Architecture</h2>
            <span className="card-subtitle">
              Resource-aware distributed coordination pipeline
            </span>
          </div>
        </div>

        <div className="arch-flow-row">
          <div className="arch-node">
            <span className="brand-spec">INGRESS</span>
            <span className="arch-node-title">Client / UI</span>
            <span className="arch-node-sub">REST :8080</span>
          </div>

          <div className="arch-connector">──►</div>

          <div className="arch-node" style={{ borderColor: 'var(--ink)' }}>
            <span className="brand-spec" style={{ color: 'var(--ink)' }}>CORE ENGINE</span>
            <span className="arch-node-title">Job Coordinator</span>
            <span className="arch-node-sub">Lifecycle & State Machine</span>
          </div>

          <div className="arch-connector">──►</div>

          <div className="arch-node" style={{ borderColor: 'var(--ink)' }}>
            <span className="brand-spec" style={{ color: 'var(--ink)' }}>PLUGGABLE</span>
            <span className="arch-node-title">Strategy Engine</span>
            <span className="arch-node-sub">5 Placement Policies</span>
          </div>

          <div className="arch-connector">──►</div>

          <div className="arch-node">
            <span className="brand-spec">EXECUTION</span>
            <span className="arch-node-title">Worker Cluster</span>
            <span className="arch-node-sub">Nodes 1 … N</span>
          </div>
        </div>

        {/* Persistence & Telemetry Subsystems */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px', marginTop: '20px' }}>
          <div style={{ background: '#fafafa', border: '1px solid var(--hairline)', borderRadius: '6px', padding: '12px' }}>
            <div className="brand-spec">STATE STORE</div>
            <div style={{ fontWeight: 600, color: 'var(--ink)', marginTop: '4px' }}>PostgreSQL 16</div>
            <div className="field-hint" style={{ marginTop: '2px' }}>
              ACID transaction log, persistent job lifecycle, and attempt tracking.
            </div>
          </div>

          <div style={{ background: '#fafafa', border: '1px solid var(--hairline)', borderRadius: '6px', padding: '12px' }}>
            <div className="brand-spec">FAST-PATH BUFFER</div>
            <div style={{ fontWeight: 600, color: 'var(--ink)', marginTop: '4px' }}>Redis 7</div>
            <div className="field-hint" style={{ marginTop: '2px' }}>
              Sub-millisecond queue depth evaluation and instant 429 backpressure gate.
            </div>
          </div>

          <div style={{ background: '#fafafa', border: '1px solid var(--hairline)', borderRadius: '6px', padding: '12px' }}>
            <div className="brand-spec">EVENT BUS</div>
            <div style={{ fontWeight: 600, color: 'var(--ink)', marginTop: '4px' }}>Apache Kafka</div>
            <div className="field-hint" style={{ marginTop: '2px' }}>
              Auditable event streaming for dispatch, completion, and failure recoveries.
            </div>
          </div>

          <div style={{ background: '#fafafa', border: '1px solid var(--hairline)', borderRadius: '6px', padding: '12px' }}>
            <div className="brand-spec">OBSERVABILITY</div>
            <div style={{ fontWeight: 600, color: 'var(--ink)', marginTop: '4px' }}>Prometheus & Grafana</div>
            <div className="field-hint" style={{ marginTop: '2px' }}>
              Telemetry scraping: starvation ratios, queue latency, and worker heartbeats.
            </div>
          </div>
        </div>
      </section>

      {/* Strategy Comparison Specs */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">Scheduling Algorithms</h2>
            <span className="card-subtitle">
              Mathematical guarantees and experimental trade-offs
            </span>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '14px' }}>
          <div style={{ border: '1px solid var(--hairline)', borderRadius: '6px', padding: '14px', background: 'var(--canvas-elevated)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <strong style={{ fontFamily: 'var(--font-mono)', textTransform: 'uppercase' }}>resource</strong>
              <span className="status-badge status-COMPLETED">DEFAULT</span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--body)', margin: '8px 0' }}>
              Composite score (Priority + Deadline pressure + Queue age) for ordering; <strong>Best-fit</strong> for placement.
            </p>
            <span className="field-hint">
              Assigns to the smallest sufficient worker so large jobs are never fragmented.
            </span>
          </div>

          <div style={{ border: '1px solid var(--hairline)', borderRadius: '6px', padding: '14px', background: 'var(--canvas-elevated)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <strong style={{ fontFamily: 'var(--font-mono)', textTransform: 'uppercase' }}>fair</strong>
              <span className="status-badge status-ASSIGNED">ANTI-STARVATION</span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--body)', margin: '8px 0' }}>
              Equal-quantum deficit round-robin per priority class: least-served class jumps queue.
            </p>
            <span className="field-hint">
              Prevents VIP priority saturation from starving low-priority workloads (Starvation ratio = 1.0x).
            </span>
          </div>

          <div style={{ border: '1px solid var(--hairline)', borderRadius: '6px', padding: '14px', background: 'var(--canvas-elevated)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <strong style={{ fontFamily: 'var(--font-mono)', textTransform: 'uppercase' }}>deadline</strong>
              <span className="status-badge status-QUEUED">SLA OPTIMIZED</span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--body)', margin: '8px 0' }}>
              Earliest-Deadline-First (EDF) placement onto least-utilized fitting workers.
            </p>
            <span className="field-hint">
              Guarantees zero missed deadlines on feasible workloads at the expense of average wait times.
            </span>
          </div>

          <div style={{ border: '1px solid var(--hairline)', borderRadius: '6px', padding: '14px', background: 'var(--canvas-elevated)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <strong style={{ fontFamily: 'var(--font-mono)', textTransform: 'uppercase' }}>priority</strong>
              <span className="status-badge status-FAILED">HIGH STARVATION</span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--body)', margin: '8px 0' }}>
              Strict priority ordering. VIP jobs process immediately; low priority suffers 5.2x wait.
            </p>
            <span className="field-hint">
              Serves as an experimental baseline demonstrating the mathematical failure of pure priority queueing.
            </span>
          </div>
        </div>
      </section>

      {/* Developer API & cURL Blocks */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">Developer API & cURL</h2>
            <span className="card-subtitle">
              Native HTTP endpoints matching production TRD/PRD specifications
            </span>
          </div>
        </div>

        {/* Submit Job Curl */}
        <div className="code-block-wrap">
          <div className="code-header">
            <span>POST /api/v1/jobs — Submit Workload</span>
            <button
              className="button-app-sm"
              style={{ padding: '2px 8px', fontSize: '11px' }}
              onClick={() => copyCode(submitCurl, 'submit')}
            >
              {copiedKey === 'submit' ? 'Copied!' : 'Copy cURL'}
            </button>
          </div>
          <pre className="code-content">{submitCurl}</pre>
        </div>

        {/* Run Simulation Curl */}
        <div className="code-block-wrap">
          <div className="code-header">
            <span>POST /api/v1/simulate — Run Benchmark Comparison</span>
            <button
              className="button-app-sm"
              style={{ padding: '2px 8px', fontSize: '11px' }}
              onClick={() => copyCode(simulateCurl, 'simulate')}
            >
              {copiedKey === 'simulate' ? 'Copied!' : 'Copy cURL'}
            </button>
          </div>
          <pre className="code-content">{simulateCurl}</pre>
        </div>

        {/* Switch Strategy Curl */}
        <div className="code-block-wrap">
          <div className="code-header">
            <span>POST /api/v1/strategies/active — Hot Strategy Switch</span>
            <button
              className="button-app-sm"
              style={{ padding: '2px 8px', fontSize: '11px' }}
              onClick={() => copyCode(strategyCurl, 'strat')}
            >
              {copiedKey === 'strat' ? 'Copied!' : 'Copy cURL'}
            </button>
          </div>
          <pre className="code-content">{strategyCurl}</pre>
        </div>
      </section>
    </div>
  );
}
