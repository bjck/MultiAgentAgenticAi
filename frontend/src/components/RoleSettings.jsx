import React, { useEffect, useState } from 'react';
import '../styles/RoleSettings.css';

const STRATEGIES = [
  { value: 'SIMPLE_SUMMARY', label: 'Simple summary' },
  { value: 'PROPOSAL_VOTE', label: 'Proposal + structured vote' },
  { value: 'TWO_ROUND_CONVERGE', label: 'Two-round converge' },
  { value: 'SCORECARD_RANKING', label: 'Scorecard ranking' },
];

const toPositiveInt = (value, fallback) => {
  const num = Number(value);
  if (!Number.isFinite(num) || num < 1) {
    return fallback;
  }
  return Math.floor(num);
};

const normalizeConfig = (config) => ({
  rounds: toPositiveInt(config?.rounds ?? 1, 1),
  agents: toPositiveInt(config?.agents ?? 1, 1),
  collaborationStrategy: config?.collaborationStrategy || 'SIMPLE_SUMMARY',
});

const RoleSettings = () => {
  const [defaults, setDefaults] = useState(normalizeConfig(null));
  const [roles, setRoles] = useState({});
  const [workerRoles, setWorkerRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState('');

  useEffect(() => {
    const fetchSettings = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch('/api/config/role-settings');
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        const nextDefaults = normalizeConfig(data?.defaults);
        const nextRoles = {};
        const roleList = Array.isArray(data?.workerRoles) ? data.workerRoles : [];
        roleList.forEach((role) => {
          nextRoles[role] = normalizeConfig(data?.roles?.[role]);
        });
        setDefaults(nextDefaults);
        setRoles(nextRoles);
        setWorkerRoles(roleList);
      } catch (e) {
        setError(e.message);
      } finally {
        setLoading(false);
      }
    };

    fetchSettings();
  }, []);

  const updateRoleField = (role, field, value) => {
    setRoles((prev) => ({
      ...prev,
      [role]: {
        ...(prev[role] || normalizeConfig(null)),
        [field]: value,
      },
    }));
  };

  const saveSettings = async () => {
    setSaving(true);
    setError(null);
    setMessage('');
    try {
      const payload = {
        defaults: normalizeConfig(defaults),
        roles: Object.fromEntries(
          Object.entries(roles).map(([role, cfg]) => [role, normalizeConfig(cfg)])
        ),
      };
      const response = await fetch('/api/config/role-settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      const nextDefaults = normalizeConfig(data?.defaults);
      const nextRoles = {};
      const roleList = Array.isArray(data?.workerRoles) ? data.workerRoles : workerRoles;
      roleList.forEach((role) => {
        nextRoles[role] = normalizeConfig(data?.roles?.[role]);
      });
      setDefaults(nextDefaults);
      setRoles(nextRoles);
      setWorkerRoles(roleList);
      setMessage('Saved.');
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="role-settings-container">Loading role settings...</div>;
  }

  return (
    <div className="role-settings-container">
      <h3>Role Settings</h3>

      {error && <div className="role-settings-error">Error: {error}</div>}
      {message && <div className="role-settings-message">{message}</div>}

      <section className="role-settings-section">
        <h4>Defaults</h4>
        <div className="role-settings-grid">
          <label>
            Rounds
            <input
              type="number"
              min="1"
              value={defaults.rounds}
              onChange={(e) => setDefaults({ ...defaults, rounds: Number(e.target.value) })}
            />
          </label>
          <label>
            Agents
            <input
              type="number"
              min="1"
              value={defaults.agents}
              onChange={(e) => setDefaults({ ...defaults, agents: Number(e.target.value) })}
            />
          </label>
          <label>
            Strategy
            <select
              value={defaults.collaborationStrategy}
              onChange={(e) => setDefaults({ ...defaults, collaborationStrategy: e.target.value })}
            >
              {STRATEGIES.map((strategy) => (
                <option key={strategy.value} value={strategy.value}>
                  {strategy.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      <section className="role-settings-section">
        <h4>Per Role Overrides</h4>
        {workerRoles.length === 0 && <p>No roles configured.</p>}
        {workerRoles.map((role) => {
          const cfg = roles[role] || normalizeConfig(null);
          return (
            <div key={role} className="role-settings-card">
              <div className="role-settings-card-title">{role}</div>
              <div className="role-settings-grid">
                <label>
                  Rounds
                  <input
                    type="number"
                    min="1"
                    value={cfg.rounds}
                    onChange={(e) => updateRoleField(role, 'rounds', Number(e.target.value))}
                  />
                </label>
                <label>
                  Agents
                  <input
                    type="number"
                    min="1"
                    value={cfg.agents}
                    onChange={(e) => updateRoleField(role, 'agents', Number(e.target.value))}
                  />
                </label>
                <label>
                  Strategy
                  <select
                    value={cfg.collaborationStrategy}
                    onChange={(e) => updateRoleField(role, 'collaborationStrategy', e.target.value)}
                  >
                    {STRATEGIES.map((strategy) => (
                      <option key={strategy.value} value={strategy.value}>
                        {strategy.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
          );
        })}
      </section>

      <div className="role-settings-actions">
        <button type="button" onClick={saveSettings} disabled={saving}>
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
      </div>
    </div>
  );
};

export default RoleSettings;
