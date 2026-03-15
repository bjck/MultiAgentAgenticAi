import React, { useEffect, useMemo, useState } from 'react';
import { useWebSocket } from '../context/WebSocketProvider';
import '../styles/Dashboard.css';

const formatTime = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleTimeString();
  } catch (e) {
    return '—';
  }
};

const Dashboard = () => {
  const { plan, sessionId, isAgentWorking, runId } = useWebSocket();
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [skillsConfig, setSkillsConfig] = useState(null);
  const [roleSettings, setRoleSettings] = useState(null);
  const [configError, setConfigError] = useState('');

  useEffect(() => {
    if (plan) {
      setLastUpdatedAt(Date.now());
    }
  }, [plan]);

  useEffect(() => {
    let cancelled = false;
    const loadConfig = async () => {
      try {
        const [skillsRes, rolesRes] = await Promise.all([
          fetch('/api/config/skills'),
          fetch('/api/config/role-settings'),
        ]);

        if (!skillsRes.ok || !rolesRes.ok) {
          throw new Error('Failed to load config.');
        }

        const skillsJson = await skillsRes.json();
        const rolesJson = await rolesRes.json();

        if (!cancelled) {
          setSkillsConfig(skillsJson);
          setRoleSettings(rolesJson);
          setConfigError('');
        }
      } catch (err) {
        if (!cancelled) {
          setConfigError(err.message || 'Failed to load config.');
        }
      }
    };

    loadConfig();
    return () => {
      cancelled = true;
    };
  }, []);

  const tasks = plan?.tasks || [];
  const skillPlans = plan?.skillPlans || [];
  const selectedSkillCount = skillPlans.reduce((sum, item) => sum + (item?.skills?.length || 0), 0);
  const totalBudget = skillPlans.reduce((sum, item) => sum + (item?.budget || 0), 0);
  const planStatus = plan?.status || 'IDLE';

  const libraryRoles = useMemo(() => {
    const roles = skillsConfig?.workerRoles || [];
    const workerSkills = skillsConfig?.workers || {};
    const defaults = skillsConfig?.workerDefaults || [];
    return roles.map((role) => ({
      role,
      skills: [...defaults, ...(workerSkills[role] || [])],
    }));
  }, [skillsConfig]);

  return (
    <div className="dashboard">
      <div className="dashboard-hero">
        <div className="dashboard-title">
          <p className="dashboard-eyebrow">Agent Library</p>
          <h2>Planning Control Center</h2>
          <p className="dashboard-subtitle">
            Monitor plans, track skill selection, and keep agent context lean without losing visibility.
          </p>
        </div>
        <div className="dashboard-status">
          <div className="status-pill">
            Session <span>{sessionId || '—'}</span>
          </div>
          <div className="status-pill">
            Run <span>{runId || '—'}</span>
          </div>
          <div className={`status-pill ${isAgentWorking ? 'status-live' : ''}`}>
            {isAgentWorking ? 'Active' : 'Idle'}
          </div>
        </div>
      </div>

      <section className="dashboard-kpis">
        <div className="kpi-card">
          <p>Plan status</p>
          <h3>{planStatus}</h3>
          <span>Updated {formatTime(lastUpdatedAt)}</span>
        </div>
        <div className="kpi-card">
          <p>Tasks</p>
          <h3>{tasks.length}</h3>
          <span>Current plan</span>
        </div>
        <div className="kpi-card">
          <p>Selected skills</p>
          <h3>{selectedSkillCount}</h3>
          <span>Budget {totalBudget}</span>
        </div>
        <div className="kpi-card">
          <p>Agents in DB</p>
          <h3>{libraryRoles.length || 0}</h3>
          <span>Active roles</span>
        </div>
      </section>

      <div className="dashboard-grid">
        <section className="dashboard-card card-plan">
          <div className="card-header">
            <h3>Current Plan</h3>
            <span className="card-tag">{planStatus}</span>
          </div>
          {plan ? (
            <>
              <p className="card-subtitle">{plan.objective || 'No objective set.'}</p>
              <div className="task-list">
                {tasks.length === 0 && <p className="card-muted">No tasks in the current plan.</p>}
                {tasks.map((task, index) => (
                  <div className="task-row" key={`${task.id}-${index}`}>
                    <div className="task-role">{task.role || 'role'}</div>
                    <div>
                      <div className="task-desc">{task.description || 'No description provided.'}</div>
                      {task.expectedOutput && <div className="task-output">{task.expectedOutput}</div>}
                    </div>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className="card-muted">Run a plan to see task details.</p>
          )}
        </section>

        <section className="dashboard-card card-skills">
          <div className="card-header">
            <h3>Skill Planning</h3>
            <span className="card-tag">Dry run</span>
          </div>
          {skillPlans.length === 0 && <p className="card-muted">No skill plans generated yet.</p>}
          {skillPlans.map((item) => (
            <details className="skill-row" key={`${item.taskId}-${item.role}`}>
              <summary>
                <span className="skill-task">{item.taskId || 'task'}</span>
                <span className="skill-role">{item.role || 'role'}</span>
                <span className="skill-count">
                  {item.skills?.length || 0}/{item.budget ?? 0}
                </span>
              </summary>
              <div className="skill-body">
                {item.rationale && <p>{item.rationale}</p>}
                <div className="skill-chip-grid">
                  {(item.skills || []).map((skill) => (
                    <div className="skill-chip" key={`${item.taskId}-${skill.name}`}>
                      <strong>{skill.name}</strong>
                      {skill.description && <span>{skill.description}</span>}
                    </div>
                  ))}
                </div>
              </div>
            </details>
          ))}
        </section>

        <section className="dashboard-card card-library">
          <div className="card-header">
            <h3>Agent Library</h3>
            <span className="card-tag">DB</span>
          </div>
          {configError && <p className="card-muted">{configError}</p>}
          {!configError && !skillsConfig && <p className="card-muted">Loading library…</p>}
          {skillsConfig && (
            <>
              <div className="library-defaults">
                <p className="library-label">Skill defaults</p>
                <div className="library-tags">
                  {(skillsConfig.workerDefaults || []).length === 0 && (
                    <span className="library-muted">No default skills.</span>
                  )}
                  {(skillsConfig.workerDefaults || []).map((skill) => (
                    <span className="library-tag" key={skill.name}>{skill.name}</span>
                  ))}
                </div>
              </div>
              <div className="library-grid">
                {libraryRoles.length === 0 && (
                  <p className="card-muted">No active worker roles found in the database.</p>
                )}
                {libraryRoles.map((entry) => (
                  <div className="library-card" key={entry.role}>
                    <div className="library-header">
                      <h4>{entry.role}</h4>
                      <span>{entry.skills.length} skills</span>
                    </div>
                    <div className="library-tags">
                      {entry.skills.length === 0 && <span className="library-muted">No skills configured.</span>}
                      {entry.skills.map((skill) => (
                        <span className="library-tag" key={`${entry.role}-${skill.name}`}>{skill.name}</span>
                      ))}
                    </div>
                    {roleSettings?.roles?.[entry.role] && (
                      <div className="library-meta">
                        <span>Rounds: {roleSettings.roles[entry.role].rounds}</span>
                        <span>Agents: {roleSettings.roles[entry.role].agents}</span>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </>
          )}
        </section>

        <section className="dashboard-card card-activity">
          <div className="card-header">
            <h3>Run Activity</h3>
            <span className="card-tag">{isAgentWorking ? 'Live' : 'Idle'}</span>
          </div>
          <div className="activity-grid">
            <div>
              <p>Session</p>
              <strong>{sessionId || '—'}</strong>
            </div>
            <div>
              <p>Run</p>
              <strong>{runId || '—'}</strong>
            </div>
            <div>
              <p>Status</p>
              <strong>{planStatus}</strong>
            </div>
            <div>
              <p>Updated</p>
              <strong>{formatTime(lastUpdatedAt)}</strong>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};

export default Dashboard;
