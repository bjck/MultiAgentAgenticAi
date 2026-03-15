import React, { useEffect, useState } from 'react';
import '../styles/Dashboard.css';

const AgentList = ({ onSelect, refreshSignal = 0 }) => {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const res = await fetch('/api/agents');
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const data = await res.json();
        setAgents(Array.isArray(data) ? data : []);
      } catch (e) {
        setError(e.message || 'Failed to load agents.');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [refreshSignal]);

  if (loading) {
    return <div className="dashboard-card">Loading agents...</div>;
  }

  if (error) {
    return <div className="dashboard-card">Error: {error}</div>;
  }

  return (
    <div className="dashboard-card card-library">
      <div className="card-header">
        <h3>Agents</h3>
        <span className="card-tag">{agents.length}</span>
      </div>
      {agents.length === 0 && <p className="card-muted">No agents created yet.</p>}
      <div className="library-grid">
        {agents.map((agent) => (
          <button
            key={agent.id}
            type="button"
            className="library-card"
            onClick={() => onSelect?.(agent.id)}
          >
            <div className="library-header">
              <h4>{agent.name}</h4>
              <span>{agent.enabled ? 'Enabled' : 'Disabled'}</span>
            </div>
            <p className="card-subtitle">
              {agent.description || agent.objectivePrompt || 'No description.'}
            </p>
            <div className="library-meta">
              <span>Next run: {agent.nextRunAt ? new Date(agent.nextRunAt).toLocaleString() : '—'}</span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
};

export default AgentList;

