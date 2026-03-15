import React, { useEffect, useState } from 'react';
import '../styles/McpServerSettings.css';

const defaultServer = () => ({
  name: '',
  transport: 'SSE',
  endpointUrl: '',
  headersJson: '',
  enabled: true,
});

const McpServerSettings = () => {
  const [servers, setServers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const loadServers = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await fetch('/api/config/mcp-servers');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      setServers(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message || 'Failed to load MCP servers.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadServers();
  }, []);

  const updateServer = (index, field, value) => {
    setServers((prev) =>
      prev.map((server, idx) =>
        idx === index ? { ...server, [field]: value } : server
      )
    );
  };

  const addServer = () => {
    setServers((prev) => [...prev, defaultServer()]);
  };

  const removeServer = (index) => {
    setServers((prev) => prev.filter((_, idx) => idx !== index));
  };

  const save = async () => {
    setSaving(true);
    setMessage('');
    setError('');
    try {
      const response = await fetch('/api/config/mcp-servers', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ servers }),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      setServers(Array.isArray(data) ? data : []);
      setMessage('Saved.');
    } catch (e) {
      setError(e.message || 'Failed to save MCP servers.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="mcp-settings">Loading MCP servers...</div>;
  }

  return (
    <div className="mcp-settings">
      <h3>MCP Servers</h3>
      <p className="mcp-settings-subtitle">
        Configure MCP connections (HTTP/SSE). Tools are loaded from the enabled servers.
      </p>

      {error && <div className="mcp-settings-error">Error: {error}</div>}
      {message && <div className="mcp-settings-message">{message}</div>}

      {servers.length === 0 && (
        <div className="mcp-settings-empty">
          No MCP servers configured.
        </div>
      )}

      <div className="mcp-settings-list">
        {servers.map((server, index) => (
          <div key={`${server.name || 'server'}-${index}`} className="mcp-settings-card">
            <div className="mcp-settings-row">
              <label>Name</label>
              <input
                type="text"
                value={server.name || ''}
                onChange={(e) => updateServer(index, 'name', e.target.value)}
                placeholder="http-fetch"
              />
            </div>
            <div className="mcp-settings-row">
              <label>Transport</label>
              <select
                value={server.transport || 'SSE'}
                onChange={(e) => updateServer(index, 'transport', e.target.value)}
              >
                <option value="SSE">SSE</option>
                <option value="STREAMABLE_HTTP">STREAMABLE_HTTP</option>
              </select>
            </div>
            <div className="mcp-settings-row">
              <label>Endpoint URL</label>
              <input
                type="text"
                value={server.endpointUrl || ''}
                onChange={(e) => updateServer(index, 'endpointUrl', e.target.value)}
                placeholder="https://host/sse or https://host/mcp"
              />
            </div>
            <div className="mcp-settings-row">
              <label>Headers (JSON)</label>
              <textarea
                rows="3"
                value={server.headersJson || ''}
                onChange={(e) => updateServer(index, 'headersJson', e.target.value)}
                placeholder='{"Authorization":"Bearer ..."}'
              />
            </div>
            <div className="mcp-settings-row mcp-settings-toggle">
              <label>Enabled</label>
              <input
                type="checkbox"
                checked={server.enabled ?? true}
                onChange={(e) => updateServer(index, 'enabled', e.target.checked)}
              />
            </div>
            <div className="mcp-settings-actions">
              <button type="button" className="mcp-remove" onClick={() => removeServer(index)}>
                Remove
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className="mcp-settings-footer">
        <button type="button" onClick={addServer}>
          Add MCP Server
        </button>
        <button type="button" onClick={save} disabled={saving}>
          {saving ? 'Saving...' : 'Save MCP Servers'}
        </button>
      </div>
    </div>
  );
};

export default McpServerSettings;
