import React, { useEffect, useMemo, useState } from 'react';
import '../styles/ToolSettings.css';

const ToolSettings = () => {
  const [availableTools, setAvailableTools] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const response = await fetch('/api/config/tools');
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        setAvailableTools(data?.availableTools || []);
        setRoles(data?.roles || []);
      } catch (e) {
        setError(e.message || 'Failed to load tool policies.');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const roleMap = useMemo(() => {
    const map = new Map();
    roles.forEach((role) => {
      const key = `${role.phase}:${role.code || 'default'}`;
      map.set(key, role);
    });
    return map;
  }, [roles]);

  const toggleTool = (roleKey, tool) => {
    setRoles((prev) =>
      prev.map((role) => {
        const key = `${role.phase}:${role.code || 'default'}`;
        if (key !== roleKey) {
          return role;
        }
        const tools = new Set(role.tools || []);
        if (tools.has(tool)) {
          tools.delete(tool);
        } else {
          tools.add(tool);
        }
        return { ...role, tools: Array.from(tools) };
      })
    );
  };

  const save = async () => {
    setSaving(true);
    setMessage('');
    setError('');
    try {
      const payload = {
        roles: roles.map((role) => ({
          phase: role.phase,
          code: role.code,
          tools: role.tools || [],
        })),
      };
      const response = await fetch('/api/config/tools', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      setAvailableTools(data?.availableTools || []);
      setRoles(data?.roles || []);
      setMessage('Saved.');
    } catch (e) {
      setError(e.message || 'Failed to save.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="tool-settings">Loading tool policies...</div>;
  }

  return (
    <div className="tool-settings">
      <h3>Tool Policies</h3>
      <p className="tool-settings-subtitle">
        Tool access is controlled by the database. Enable only what each role truly needs.
      </p>

      {error && <div className="tool-settings-error">Error: {error}</div>}
      {message && <div className="tool-settings-message">{message}</div>}

      {availableTools.length === 0 && <p>No tools registered.</p>}

      <div className="tool-settings-grid">
        {roles.map((role) => {
          const key = `${role.phase}:${role.code || 'default'}`;
          return (
            <div key={key} className="tool-role-card">
              <div className="tool-role-header">
                <div>
                  <strong>{role.displayName || role.code || role.phase}</strong>
                  <div className="tool-role-meta">{role.phase}{role.code ? ` · ${role.code}` : ''}</div>
                </div>
                <span className="tool-count">{role.tools?.length || 0}</span>
              </div>
              <div className="tool-role-options">
                {availableTools.map((tool) => {
                  const checked = role.tools?.includes(tool);
                  return (
                    <label key={`${key}-${tool}`} className={`tool-option ${checked ? 'active' : ''}`}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleTool(key, tool)}
                      />
                      <span>{tool}</span>
                    </label>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      <div className="tool-settings-actions">
        <button type="button" onClick={save} disabled={saving}>
          {saving ? 'Saving...' : 'Save Tool Policies'}
        </button>
      </div>
    </div>
  );
};

export default ToolSettings;
