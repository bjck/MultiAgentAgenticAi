import React, { useEffect, useState } from 'react';
import '../styles/FileEditor.css';

const FileEditor = ({ filePath, onCancel, onSaveSuccess }) => {
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!filePath) {
      return;
    }
    const fetchFile = async () => {
      setLoading(true);
      setError(null);
      setMessage('');
      try {
        const response = await fetch(`/api/files/content?path=${encodeURIComponent(filePath)}`);
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        setContent(data.content ?? '');
      } catch (e) {
        setError(e.message);
      } finally {
        setLoading(false);
      }
    };
    fetchFile();
  }, [filePath]);

  const handleSave = async () => {
    if (!filePath) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage('');
    try {
      const response = await fetch('/api/files/content', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: filePath, content })
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      setMessage('Saved.');
      if (onSaveSuccess) {
        onSaveSuccess();
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  if (!filePath) {
    return <div className="file-editor-container">Select a file to view or edit.</div>;
  }

  if (loading) {
    return <div className="file-editor-container">Loading file...</div>;
  }

  return (
    <div className="file-editor-container">
      <div className="editor-header">
        <h3>{filePath}</h3>
        <button
          type="button"
          className="close-button"
          onClick={() => onCancel && onCancel()}
        >
          x
        </button>
      </div>
      {error && <div className="file-editor-container error">Error: {error}</div>}
      <textarea
        className="file-content-textarea"
        value={content}
        onChange={(event) => setContent(event.target.value)}
      />
      <div className="editor-actions">
        <button type="button" className="save-button" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save'}
        </button>
        {message && <span>{message}</span>}
      </div>
    </div>
  );
};

export default FileEditor;
