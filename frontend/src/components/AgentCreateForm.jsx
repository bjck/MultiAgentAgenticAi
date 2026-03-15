import React, { useState } from 'react';
import '../styles/Dashboard.css';

const AgentCreateForm = ({ onCreated }) => {
  const [name, setName] = useState('');
  const [objective, setObjective] = useState('');
  const [schedule, setSchedule] = useState('every hour');
  const [description, setDescription] = useState('');
  const [tokenLimit, setTokenLimit] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    if (!name.trim() || !objective.trim()) {
      setError('Name and objective are required.');
      return;
    }
    setSaving(true);
    try {
      const body = {
        name: name.trim(),
        description: description.trim() || null,
        objectivePrompt: objective.trim(),
        rawScheduleInput: schedule.trim() || null,
        tokenLimitPerRun: tokenLimit ? Number(tokenLimit) : null,
      };
      const res = await fetch('/api/agents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const created = await res.json();
      setName('');
      setObjective('');
      setSchedule('every hour');
      setDescription('');
      setTokenLimit('');
      onCreated?.(created);
    } catch (e) {
      setError(e.message || 'Failed to create agent.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="dashboard-card card-plan" onSubmit={submit}>
      <div className="card-header">
        <h3>Create Agent</h3>
        <span className="card-tag">Natural language</span>
      </div>
      <p className="card-subtitle">
        Describe what the agent should do and how often it should run. The backend will interpret the schedule.
      </p>
      <div className="dashboard-section">
        <label>
          Name
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Arxiv hourly fetcher"
          />
        </label>
        <label>
          What should this agent do?
          <textarea
            rows={3}
            value={objective}
            onChange={(e) => setObjective(e.target.value)}
            placeholder='Example: "Every hour, fetch the latest cs.SE papers from arxiv.org and store their abstracts."'
          />
        </label>
        <label>
          Schedule (natural language or cron)
          <input
            type="text"
            value={schedule}
            onChange={(e) => setSchedule(e.target.value)}
            placeholder='Examples: "every hour", "daily", or cron like "0 0 * * * *"'
          />
        </label>
        <label>
          Notes (optional)
          <textarea
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Any extra context for humans."
          />
        </label>
        <label>
          Token limit per run (optional)
          <input
            type="number"
            min="0"
            value={tokenLimit}
            onChange={(e) => setTokenLimit(e.target.value)}
            placeholder="e.g. 50000"
          />
        </label>
        {error && <div className="card-muted">Error: {error}</div>}
        <button type="submit" disabled={saving}>
          {saving ? 'Creating...' : 'Create agent'}
        </button>
      </div>
    </form>
  );
};

export default AgentCreateForm;
