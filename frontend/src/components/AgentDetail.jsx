import React, { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/Dashboard.css';

const AgentDetail = ({ agentId, onDeleted }) => {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [answer, setAnswer] = useState('');
  const [queryLoading, setQueryLoading] = useState(false);
  const [queryError, setQueryError] = useState('');
  const [runNowLoading, setRunNowLoading] = useState(false);
  const [runEvents, setRunEvents] = useState({});
  const [runEventsLoading, setRunEventsLoading] = useState({});
  const [runEventsError, setRunEventsError] = useState({});
  /** Event opened in the detail modal (full input/output view). */
  const [inspectedEvent, setInspectedEvent] = useState(null);
  const [eventDetailTab, setEventDetailTab] = useState('input');

  useEffect(() => {
    if (!agentId) return;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const res = await fetch(`/api/agents/${agentId}`);
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const data = await res.json();
        setDetail(data);
      } catch (e) {
        setError(e.message || 'Failed to load agent.');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [agentId]);

  useEffect(() => {
    if (!agentId) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/agent-runs?agentId=${encodeURIComponent(agentId)}`;
    const ws = new WebSocket(wsUrl);
    ws.onmessage = () => {
      fetch(`/api/agents/${agentId}`)
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data) setDetail(data);
        })
        .catch(() => {});
    };
    return () => ws.close();
  }, [agentId]);

  const runQuery = async () => {
    if (!agentId || !query.trim()) return;
    setQueryLoading(true);
    setQueryError('');
    setAnswer('');
    try {
      const body = {
        query,
      };
      const res = await fetch(`/api/agents/${agentId}/query`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      setAnswer(data?.answer || '');
    } catch (e) {
      setQueryError(e.message || 'Failed to run query.');
    } finally {
      setQueryLoading(false);
    }
  };

  const runNow = async () => {
    if (!agentId) return;
    setRunNowLoading(true);
    try {
      const res = await fetch(`/api/agents/${agentId}/run`, { method: 'POST' });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const refreshed = await fetch(`/api/agents/${agentId}`);
      if (refreshed.ok) {
        const data = await refreshed.json();
        setDetail(data);
      }
    } catch (e) {
      setQueryError(e.message || 'Failed to run agent.');
    } finally {
      setRunNowLoading(false);
    }
  };

  const deleteAgent = async () => {
    if (!agentId) return;
    const confirmed = window.confirm('Delete this agent? This will also delete its run history.');
    if (!confirmed) return;
    try {
      const res = await fetch(`/api/agents/${agentId}`, { method: 'DELETE' });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      onDeleted?.();
    } catch (e) {
      setQueryError(e.message || 'Failed to delete agent.');
    }
  };

  const formatDateTime = (value) => {
    if (!value) return 'N/A';
    try {
      return new Date(value).toLocaleString();
    } catch (e) {
      return 'N/A';
    }
  };

  const fetchRunEvents = async (runId) => {
    if (!agentId || !runId) return;
    if (runEventsLoading[runId] || runEvents[runId]) return;
    setRunEventsLoading((prev) => ({ ...prev, [runId]: true }));
    setRunEventsError((prev) => ({ ...prev, [runId]: '' }));
    try {
      const res = await fetch(`/api/agents/${agentId}/runs/${runId}/events`);
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      setRunEvents((prev) => ({ ...prev, [runId]: data || [] }));
    } catch (e) {
      setRunEventsError((prev) => ({ ...prev, [runId]: e.message || 'Failed to load events.' }));
    } finally {
      setRunEventsLoading((prev) => ({ ...prev, [runId]: false }));
    }
  };

  if (!agentId) {
    return (
      <div className="dashboard-card">
        <p className="card-muted">Select an agent to view details.</p>
      </div>
    );
  }

  if (loading) {
    return <div className="dashboard-card">Loading agent...</div>;
  }

  if (error) {
    return <div className="dashboard-card">Error: {error}</div>;
  }

  const runs = detail?.runs || [];

  return (
    <div className="dashboard-card card-plan">
      <div className="card-header">
        <h3>{detail.name}</h3>
        <span className="card-tag">{detail.enabled ? 'Enabled' : 'Disabled'}</span>
      </div>
      <p className="card-subtitle">{detail.description || detail.objectivePrompt}</p>
      <div className="activity-grid">
        <div>
          <p>Schedule</p>
          <strong>{detail.rawScheduleInput || detail.scheduleExpression}</strong>
        </div>
        <div>
          <p>Last run</p>
          <strong>{detail.lastRunAt ? new Date(detail.lastRunAt).toLocaleString() : 'N/A'}</strong>
        </div>
        <div>
          <p>Next run</p>
          <strong>{detail.nextRunAt ? new Date(detail.nextRunAt).toLocaleString() : 'N/A'}</strong>
        </div>
        <div>
          <p>Token limit / run</p>
          <strong>{detail.tokenLimitPerRun ?? 'N/A'}</strong>
        </div>
      </div>

      <div className="dashboard-section">
        <h4>Ask this agent</h4>
        <p className="card-muted">
          Ask a single question about this agent&apos;s stored results. The answer will be summarized from the database.
        </p>
        <textarea
          rows={3}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Summarize the software engineering papers from the past few days."
        />
        <div className="query-filters">
          <button type="button" onClick={runQuery} disabled={queryLoading || !query.trim()}>
            {queryLoading ? 'Running...' : 'Run query'}
          </button>
          <button type="button" onClick={runNow} disabled={runNowLoading}>
            {runNowLoading ? 'Running...' : 'Run now'}
          </button>
          <button type="button" className="danger-button" onClick={deleteAgent}>
            Delete agent
          </button>
        </div>
        {queryError && <div className="card-muted">Error: {queryError}</div>}
        {answer && (
          <div className="query-answer">
            <h5>Answer</h5>
            <ReactMarkdown className="markdown-body">{answer}</ReactMarkdown>
          </div>
        )}
      </div>

      <div className="dashboard-section">
        <h4>Runs</h4>
        {runs.length === 0 && <p className="card-muted">Run the agent to capture events.</p>}
        <div className="event-log">
          {runs.map((run) => (
            <details
              key={run.id}
              className="event-log-run"
              onToggle={(event) => {
                if (event.currentTarget.open) {
                  fetchRunEvents(run.id);
                }
              }}
            >
              <summary>
                <div className="event-log-summary">
                  <div>
                    <strong>{formatDateTime(run.startedAt)}</strong>
                    <div className="card-muted">Status: {run.status}</div>
                    {run.errorMessage && <div className="card-muted">{run.errorMessage}</div>}
                  </div>
                  <div className="event-log-meta">
                    <span>Tokens: {run.totalTokens ?? 0}</span>
                    <span>Events: {run.eventCount ?? 0}</span>
                  </div>
                </div>
              </summary>
              {runEventsLoading[run.id] && <div className="card-muted">Loading events...</div>}
              {runEventsError[run.id] && <div className="card-muted">Error: {runEventsError[run.id]}</div>}
              {runEvents[run.id] && runEvents[run.id].length === 0 && !runEventsLoading[run.id] && (
                <div className="card-muted">No LLM events recorded for this run.</div>
              )}
              {runEvents[run.id] && runEvents[run.id].length > 0 && (
                <div className="event-log-table-wrap">
                  <div className="event-log-table">
                    <div className="event-log-header">
                      <span>Time</span>
                      <span>Purpose</span>
                      <span>Role</span>
                      <span>Tokens</span>
                      <span>Inspect</span>
                    </div>
                    {runEvents[run.id].map((evt, index) => (
                      <div key={`${run.id}-${index}`} className="event-log-row">
                        <div className="event-log-cell" data-label="Time">{formatDateTime(evt.createdAt)}</div>
                        <div className="event-log-cell" data-label="Purpose">{evt.purpose || 'N/A'}</div>
                        <div className="event-log-cell" data-label="Role">{evt.role || 'N/A'}</div>
                        <div className="event-log-cell" data-label="Tokens">{evt.totalTokens ?? 0}</div>
                        <div className="event-log-cell" data-label="Inspect">
                          <button
                            type="button"
                            className="event-inspect-btn"
                            onClick={() => {
                              setInspectedEvent({ evt, runId: run.id });
                              setEventDetailTab('input');
                            }}
                          >
                            View request / response
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </details>
          ))}
        </div>
      </div>

      {inspectedEvent && (
        <dialog
          className="event-detail-overlay"
          open
          aria-labelledby="event-detail-title"
          onCancel={(e) => { e.preventDefault(); setInspectedEvent(null); }}
          onClick={(e) => e.target === e.currentTarget && setInspectedEvent(null)}
          onKeyDown={(e) => e.key === 'Escape' && setInspectedEvent(null)}
        >
          <div className="event-detail-modal">
            <div className="event-detail-header">
              <h3 id="event-detail-title">
                LLM event: {inspectedEvent.evt.purpose || 'N/A'}
                {inspectedEvent.evt.role ? ` · ${inspectedEvent.evt.role}` : ''}
              </h3>
              <div className="event-detail-meta">
                <span>{formatDateTime(inspectedEvent.evt.createdAt)}</span>
                <span>Tokens: {inspectedEvent.evt.totalTokens ?? 0}</span>
              </div>
              <button
                type="button"
                className="event-detail-close"
                onClick={() => setInspectedEvent(null)}
                aria-label="Close"
              >
                ×
              </button>
            </div>
            <p className="event-detail-hint card-muted">
              This is one LLM request/response pair. View full prompt and model output below.
            </p>
            <div className="event-detail-tabs">
              <button
                type="button"
                className={eventDetailTab === 'input' ? 'active' : ''}
                onClick={() => setEventDetailTab('input')}
              >
                Input (prompt)
              </button>
              <button
                type="button"
                className={eventDetailTab === 'output' ? 'active' : ''}
                onClick={() => setEventDetailTab('output')}
              >
                Output (response)
              </button>
            </div>
            <div className="event-detail-body">
              {eventDetailTab === 'input' && (
                <div className="event-detail-pane">
                  <pre className="event-detail-pre">
                    {inspectedEvent.evt.input || 'No input recorded.'}
                  </pre>
                </div>
              )}
              {eventDetailTab === 'output' && (
                <div className="event-detail-pane">
                  <pre className="event-detail-pre">
                    {inspectedEvent.evt.output || 'No output recorded.'}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </dialog>
      )}
    </div>
  );
};

export default AgentDetail;
