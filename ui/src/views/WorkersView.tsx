import { useCallback, useEffect, useState } from 'react';
import { api, type WorkerView } from '../api.ts';

function usage(used: number, total: number): number {
  if (total <= 0) return 0;
  return Math.min(1, Math.max(0, used / total));
}

interface WorkersViewProps {
  onNotify?: (message: string, type?: 'info' | 'error' | 'warn') => void;
}

export default function WorkersView({ onNotify }: WorkersViewProps) {
  const [workers, setWorkers] = useState<WorkerView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setWorkers(await api.listWorkers());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load workers');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 5000);
    return () => clearInterval(t);
  }, [load]);

  function ago(iso: string): string {
    const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (s < 60) return `${s}s ago`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m ago`;
    return `${Math.floor(m / 60)}h ago`;
  }

  function copyId(id: string) {
    void navigator.clipboard.writeText(id);
    if (onNotify) onNotify(`Copied worker ID: ${id}`, 'info');
  }

  // Aggregate metrics
  const totalCores = workers.reduce((acc, w) => acc + w.totalCpu, 0);
  const usedCores = workers.reduce((acc, w) => acc + (w.totalCpu - w.availableCpu), 0);
  const totalRamMb = workers.reduce((acc, w) => acc + w.totalMemoryMb, 0);
  const usedRamMb = workers.reduce((acc, w) => acc + (w.totalMemoryMb - w.availableMemoryMb), 0);
  const totalRunning = workers.reduce((acc, w) => acc + w.runningJobs, 0);

  const cpuClusterPct = totalCores > 0 ? Math.round((usedCores / totalCores) * 100) : 0;
  const ramClusterPct = totalRamMb > 0 ? Math.round((usedRamMb / totalRamMb) * 100) : 0;

  return (
    <div>
      {/* Cluster Overview Bar */}
      <div className="workers-summary-bar">
        <div className="workers-summary-item">
          <span className="brand-spec">Cluster Capacity:</span>
          <span>
            <strong>{workers.length}</strong> Nodes
          </span>
        </div>

        <div className="workers-summary-item">
          <span className="brand-spec">CPU Allocated:</span>
          <span>
            <strong>{usedCores}</strong> / {totalCores} Cores ({cpuClusterPct}%)
          </span>
        </div>

        <div className="workers-summary-item">
          <span className="brand-spec">RAM Allocated:</span>
          <span>
            <strong>{(usedRamMb / 1024).toFixed(1)} GB</strong> / {(totalRamMb / 1024).toFixed(1)} GB ({ramClusterPct}%)
          </span>
        </div>

        <div className="workers-summary-item">
          <span className="brand-spec">Active Jobs:</span>
          <span>
            <strong>{totalRunning}</strong> executing
          </span>
        </div>

        <button className="button-app-sm" onClick={() => void load()} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh Nodes'}
        </button>
      </div>

      {/* Main Workers Section */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">Worker Nodes</h2>
            <span className="card-subtitle">
              Heterogeneous execution agents registered with coordinator
            </span>
          </div>
        </div>

        {loading && workers.length === 0 && (
          <div className="notice-box info">Discovering cluster nodes…</div>
        )}

        {error && <div className="notice-box error">{error}</div>}

        {!loading && !error && workers.length === 0 && (
          <div style={{ padding: '36px 0', textAlign: 'center', color: 'var(--mute)' }}>
            <p style={{ fontSize: '14px', marginBottom: '4px' }}>No workers registered</p>
            <p className="field-hint">Start worker instances to register them via heartbeat.</p>
          </div>
        )}

        <div className="worker-cards-grid">
          {workers.map((w) => {
            const usedCpu = w.totalCpu - w.availableCpu;
            const usedMem = w.totalMemoryMb - w.availableMemoryMb;
            const cpuPct = Math.round(usage(usedCpu, w.totalCpu) * 100);
            const memPct = Math.round(usage(usedMem, w.totalMemoryMb) * 100);

            const isHealthy = w.health === 'HEALTHY';
            const isFailed = w.health === 'FAILED';

            return (
              <div
                key={w.id}
                className={`worker-card ${isFailed ? 'failed' : ''}`}
              >
                <div className="worker-header">
                  <div className="worker-id-group">
                    <span className="worker-id-text" title="Click to copy ID" onClick={() => copyId(w.id)} style={{ cursor: 'pointer' }}>
                      {w.id}
                    </span>
                    <button
                      className="button-icon-circular"
                      style={{ width: '22px', height: '22px', fontSize: '10px' }}
                      title="Copy ID"
                      onClick={() => copyId(w.id)}
                    >
                      📋
                    </button>
                  </div>

                  <span
                    className={`status-badge ${
                      isHealthy
                        ? 'status-COMPLETED'
                        : isFailed
                        ? 'status-FAILED'
                        : 'status-ASSIGNED'
                    }`}
                  >
                    {w.health}
                  </span>
                </div>

                <div style={{ marginBottom: '12px' }}>
                  <span className="worker-address-text">{w.address}</span>
                </div>

                {/* CPU Meter */}
                <div className="resource-meter">
                  <div className="resource-meter-header">
                    <span>CPU Allocation</span>
                    <span>
                      <b>{usedCpu}</b> / {w.totalCpu} cores ({cpuPct}%)
                    </span>
                  </div>
                  <div className="progress-track">
                    <div
                      className="progress-fill"
                      style={{ width: `${cpuPct}%` }}
                    />
                  </div>
                </div>

                {/* Memory Meter */}
                <div className="resource-meter">
                  <div className="resource-meter-header">
                    <span>Memory Allocation</span>
                    <span>
                      <b>{usedMem}</b> / {w.totalMemoryMb} MB ({memPct}%)
                    </span>
                  </div>
                  <div className="progress-track">
                    <div
                      className="progress-fill"
                      style={{ width: `${memPct}%` }}
                    />
                  </div>
                </div>

                {/* Foot metadata */}
                <div className="worker-meta-foot">
                  <span>
                    Executing: <b>{w.runningJobs} jobs</b>
                  </span>
                  <span title={new Date(w.lastHeartbeat).toLocaleString()}>
                    Heartbeat: <b>{ago(w.lastHeartbeat)}</b>
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Info notice about worker heartbeat and failover protocol */}
      <div className="notice-box info" style={{ marginTop: '16px' }}>
        <span>
          <strong>Failover Protocol:</strong> Workers heartbeat every 2s. If 3 heartbeats are missed, worker enters{' '}
          <code>SUSPECTED</code>. After 5 missed beats, node transitions to <code>FAILED</code> and assigned/running jobs
          are returned to <code>QUEUED</code> with attempts incremented.
        </span>
      </div>
    </div>
  );
}
