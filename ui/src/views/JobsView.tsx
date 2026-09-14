import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, api, type JobStatus, type JobView, type SubmitJobRequest } from '../api.ts';

const STATUSES: Array<'' | JobStatus> = [
  '',
  'QUEUED',
  'ASSIGNED',
  'RUNNING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'REJECTED'
];

interface Preset {
  label: string;
  name: string;
  priority: number;
  cpu: number;
  memoryMb: number;
  deadlineMinutes?: number;
}

const PRESETS: Preset[] = [
  { label: 'Video Transcode', name: 'video-encode-h265', priority: 8, cpu: 4, memoryMb: 4096, deadlineMinutes: 30 },
  { label: 'AI Inference', name: 'llm-embedding-eval', priority: 9, cpu: 2, memoryMb: 8192, deadlineMinutes: 15 },
  { label: 'Batch ETL', name: 'warehouse-daily-sync', priority: 5, cpu: 2, memoryMb: 2048, deadlineMinutes: 120 },
  { label: 'Realtime Webhook', name: 'stripe-webhook-dispatch', priority: 10, cpu: 1, memoryMb: 512, deadlineMinutes: 5 },
  { label: 'Background Cleanup', name: 'temp-storage-vacuum', priority: 2, cpu: 1, memoryMb: 256 }
];

interface JobsViewProps {
  onNotify?: (message: string, type?: 'info' | 'error' | 'warn') => void;
}

export default function JobsView({ onNotify }: JobsViewProps) {
  const [statusFilter, setStatusFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [jobs, setJobs] = useState<JobView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [backpressureNotice, setBackpressureNotice] = useState(false);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [selectedJob, setSelectedJob] = useState<JobView | null>(null);

  const [form, setForm] = useState({
    name: '',
    priority: 5,
    cpu: 1,
    memoryMb: 512,
    deadline: ''
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listJobs(statusFilter || undefined, 100);
      setJobs(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load jobs');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 5000);
    return () => clearInterval(t);
  }, [load]);

  function set<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function applyPreset(p: Preset) {
    let deadlineStr = '';
    if (p.deadlineMinutes) {
      const d = new Date(Date.now() + p.deadlineMinutes * 60 * 1000);
      // Format as YYYY-MM-DDTHH:mm
      deadlineStr = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    }
    setForm({
      name: p.name,
      priority: p.priority,
      cpu: p.cpu,
      memoryMb: p.memoryMb,
      deadline: deadlineStr
    });
    if (onNotify) onNotify(`Applied template "${p.label}"`, 'info');
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    setBackpressureNotice(false);

    const body: SubmitJobRequest = {
      name: form.name,
      priority: Number(form.priority),
      cpu: Number(form.cpu),
      memoryMb: Number(form.memoryMb),
      deadline: form.deadline ? new Date(form.deadline).toISOString() : null
    };

    try {
      const created = await api.submitJob(body);
      setForm({ name: '', priority: 5, cpu: 1, memoryMb: 512, deadline: '' });
      if (onNotify) onNotify(`Job "${created.name}" submitted successfully`, 'info');
      await load();
    } catch (e) {
      if (e instanceof ApiError && e.status === 429) {
        setBackpressureNotice(true);
        const msg = `Backpressure active: ${e.message}. Priority < 5 submissions rejected with 429.`;
        setSubmitError(msg);
        if (onNotify) onNotify(msg, 'warn');
      } else {
        const msg = e instanceof Error ? e.message : 'Submit failed';
        setSubmitError(msg);
        if (onNotify) onNotify(msg, 'error');
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function onCancel(job: JobView) {
    if (!window.confirm(`Cancel job "${job.name}" (${job.id})?`)) return;
    setCancellingId(job.id);
    try {
      await api.cancelJob(job.id);
      if (onNotify) onNotify(`Cancelled job "${job.name}"`, 'info');
      if (selectedJob?.id === job.id) {
        setSelectedJob(null);
      }
      await load();
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Cancel failed';
      setError(msg);
      if (onNotify) onNotify(msg, 'error');
    } finally {
      setCancellingId(null);
    }
  }

  const filteredJobs = useMemo(() => {
    if (!searchQuery.trim()) return jobs;
    const q = searchQuery.toLowerCase();
    return jobs.filter(
      (j) => j.name.toLowerCase().includes(q) || j.id.toLowerCase().includes(q) || (j.workerId && j.workerId.toLowerCase().includes(q))
    );
  }, [jobs, searchQuery]);

  function prioClass(p: number): string {
    if (p >= 8) return 'prio-chip prio-high';
    if (p >= 5) return 'prio-chip prio-mid';
    return 'prio-chip prio-low';
  }

  function formatRelative(iso: string): string {
    const diffSec = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (diffSec < 60) return `${diffSec}s ago`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    return `${Math.floor(diffHours / 24)}d ago`;
  }

  return (
    <div>
      {/* Submit Job Card */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">Submit Job</h2>
            <span className="card-subtitle">Dispatch new workload to the coordinator queue</span>
          </div>
        </div>

        {/* Quick Presets */}
        <div className="presets-strip">
          <span className="presets-title">Presets:</span>
          {PRESETS.map((p) => (
            <button
              key={p.label}
              type="button"
              className="preset-chip"
              onClick={() => applyPreset(p)}
              title={`${p.cpu} CPU · ${p.memoryMb} MB · Priority ${p.priority}`}
            >
              {p.label}
            </button>
          ))}
        </div>

        <form onSubmit={onSubmit}>
          <div className="form-grid">
            <div className="form-field">
              <label className="field-label">
                Job Name
                <span className="field-hint">e.g. video-processing</span>
              </label>
              <input
                className="text-input"
                value={form.name}
                required
                onChange={(e) => set('name', e.target.value)}
                placeholder="job-identifier"
              />
            </div>

            <div className="form-field">
              <label className="field-label">
                Priority
                <span className="field-hint">1 (Lowest) – 10 (Highest)</span>
              </label>
              <input
                className="text-input"
                type="number"
                min={1}
                max={10}
                value={form.priority}
                onChange={(e) => set('priority', Number(e.target.value))}
              />
            </div>

            <div className="form-field">
              <label className="field-label">
                CPU Cores
                <span className="field-hint">Required cores</span>
              </label>
              <input
                className="text-input"
                type="number"
                min={1}
                value={form.cpu}
                onChange={(e) => set('cpu', Number(e.target.value))}
              />
            </div>

            <div className="form-field">
              <label className="field-label">
                Memory MB
                <span className="field-hint">RAM allocation</span>
              </label>
              <input
                className="text-input"
                type="number"
                min={1}
                step={64}
                value={form.memoryMb}
                onChange={(e) => set('memoryMb', Number(e.target.value))}
              />
            </div>

            <div className="form-field">
              <label className="field-label">
                Deadline
                <span className="field-hint">Optional SLA</span>
              </label>
              <input
                className="text-input"
                type="datetime-local"
                value={form.deadline}
                onChange={(e) => set('deadline', e.target.value)}
              />
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '12px' }}>
            <button className="button-app-primary-sm" type="submit" disabled={submitting || !form.name}>
              {submitting ? 'Submitting…' : 'Submit Job'}
            </button>
            {form.name && (
              <span className="field-hint">
                Requesting <b>{form.cpu} cores</b> & <b>{form.memoryMb} MB</b> @ Priority <b>{form.priority}</b>
              </span>
            )}
          </div>
        </form>

        {submitError && (
          <div className="notice-box error" style={{ marginTop: '14px' }}>
            <span>{submitError}</span>
          </div>
        )}

        {backpressureNotice && (
          <div className="backpressure-banner" style={{ marginTop: '14px' }}>
            <div className="icon">⚠</div>
            <div>
              <strong>Queue is under backpressure.</strong> Low-priority submissions (priority &lt; 5) are rejected
              with HTTP 429. Raise priority to 5+ or wait for queue depth to decrease below threshold.
            </div>
          </div>
        )}
      </section>

      {/* Jobs Listing Card */}
      <section className="feature-card">
        <div className="card-header">
          <div className="card-title-group">
            <h2 className="card-title">Jobs Queue</h2>
            <span className="card-subtitle">
              {jobs.length} total jobs {filteredJobs.length !== jobs.length && `(${filteredJobs.length} filtered)`}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            {/* Search */}
            <input
              className="text-input"
              style={{ width: '180px', height: '32px', fontSize: '12.5px' }}
              placeholder="Search by name or ID…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />

            {/* Filter */}
            <select
              className="select-input"
              style={{ height: '32px', fontSize: '12.5px', padding: '0 8px' }}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s === '' ? 'All Statuses' : s}
                </option>
              ))}
            </select>

            <button className="button-app-sm" onClick={() => void load()} disabled={loading}>
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>
        </div>

        {loading && jobs.length === 0 && (
          <div className="notice-box info">Loading jobs from coordinator…</div>
        )}

        {error && <div className="notice-box error">{error}</div>}

        {!loading && !error && filteredJobs.length === 0 && (
          <div style={{ padding: '32px 0', textAlign: 'center', color: 'var(--mute)' }}>
            <p style={{ fontSize: '14px', marginBottom: '6px' }}>No jobs match current filters</p>
            <p className="field-hint">Submit a job above or select "All Statuses".</p>
          </div>
        )}

        {filteredJobs.length > 0 && (
          <div className="table-wrap">
            <table className="geist-table">
              <thead>
                <tr>
                  <th>Job Name</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th className="numeric">CPU</th>
                  <th className="numeric">Memory</th>
                  <th>Assigned Worker</th>
                  <th>Attempts</th>
                  <th>Created</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredJobs.map((j) => (
                  <tr
                    key={j.id}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setSelectedJob(j)}
                  >
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column' }}>
                        <span style={{ fontWeight: 600, color: 'var(--ink)' }}>{j.name}</span>
                        <span className="mono-cell" style={{ fontSize: '11px', color: 'var(--mute)' }}>
                          {j.id.slice(0, 13)}…
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className={`status-badge status-${j.status}`}>{j.status}</span>
                    </td>
                    <td>
                      <span className={prioClass(j.priority)}>P{j.priority}</span>
                    </td>
                    <td className="numeric">{j.cpu}</td>
                    <td className="numeric">{j.memoryMb} MB</td>
                    <td>
                      {j.workerId ? (
                        <span className="mono-cell" style={{ fontSize: '12px' }}>
                          {j.workerId}
                        </span>
                      ) : (
                        <span style={{ color: 'var(--faint)' }}>—</span>
                      )}
                    </td>
                    <td>
                      <span style={{ fontSize: '12px', fontVariantNumeric: 'tabular-nums' }}>
                        {j.attempts} / {j.maxAttempts}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: '12.5px', color: 'var(--body)' }} title={new Date(j.createdAt).toLocaleString()}>
                        {formatRelative(j.createdAt)}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }} onClick={(e) => e.stopPropagation()}>
                      {(j.status === 'QUEUED' || j.status === 'ASSIGNED' || j.status === 'RUNNING') ? (
                        <button
                          className="button-app-danger-sm"
                          disabled={cancellingId === j.id}
                          onClick={() => void onCancel(j)}
                          title="Cancel Job"
                        >
                          {cancellingId === j.id ? 'Cancelling…' : 'Cancel'}
                        </button>
                      ) : (
                        <button
                          className="button-app-sm"
                          style={{ padding: '3px 8px', fontSize: '11px' }}
                          onClick={() => setSelectedJob(j)}
                        >
                          View
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Inspect Job Modal */}
      {selectedJob && (
        <div className="modal-backdrop" onClick={() => setSelectedJob(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h3 className="modal-title">{selectedJob.name}</h3>
                <span className={`status-badge status-${selectedJob.status}`}>{selectedJob.status}</span>
              </div>
              <button
                className="button-icon-circular"
                onClick={() => setSelectedJob(null)}
                title="Close"
              >
                ✕
              </button>
            </div>

            <div className="details-grid">
              <span className="details-label">Job ID</span>
              <span className="details-val mono-cell">{selectedJob.id}</span>

              <span className="details-label">Priority</span>
              <span className="details-val">Priority {selectedJob.priority}</span>

              <span className="details-label">Resources</span>
              <span className="details-val">{selectedJob.cpu} Cores · {selectedJob.memoryMb} MB RAM</span>

              <span className="details-label">Worker</span>
              <span className="details-val mono-cell">{selectedJob.workerId ?? 'Unassigned (Waiting in Queue)'}</span>

              <span className="details-label">Attempts</span>
              <span className="details-val">{selectedJob.attempts} of {selectedJob.maxAttempts} max retries</span>

              <span className="details-label">Created At</span>
              <span className="details-val">{new Date(selectedJob.createdAt).toLocaleString()}</span>

              {selectedJob.startedAt && (
                <>
                  <span className="details-label">Started At</span>
                  <span className="details-val">{new Date(selectedJob.startedAt).toLocaleString()}</span>
                </>
              )}

              {selectedJob.finishedAt && (
                <>
                  <span className="details-label">Finished At</span>
                  <span className="details-val">{new Date(selectedJob.finishedAt).toLocaleString()}</span>
                </>
              )}

              {selectedJob.deadline && (
                <>
                  <span className="details-label">Deadline</span>
                  <span className="details-val">{new Date(selectedJob.deadline).toLocaleString()}</span>
                </>
              )}

              {selectedJob.error && (
                <>
                  <span className="details-label" style={{ fontWeight: 600 }}>Error</span>
                  <span className="details-val" style={{ fontFamily: 'var(--font-mono)' }}>
                    {selectedJob.error}
                  </span>
                </>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              {(selectedJob.status === 'QUEUED' || selectedJob.status === 'ASSIGNED' || selectedJob.status === 'RUNNING') && (
                <button
                  className="button-app-danger-sm"
                  disabled={cancellingId === selectedJob.id}
                  onClick={() => void onCancel(selectedJob)}
                >
                  {cancellingId === selectedJob.id ? 'Cancelling…' : 'Cancel Job'}
                </button>
              )}
              <button className="button-app-sm" onClick={() => setSelectedJob(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
