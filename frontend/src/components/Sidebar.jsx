import React, { useState } from 'react';
import '../styles/Sidebar.css';
import ModelSelector from './ModelSelector';
import FileBrowser from './FileBrowser';
import FileEditor from './FileEditor';
import RoleSettings from './RoleSettings';
import ToolSettings from './ToolSettings';
import McpServerSettings from './McpServerSettings';

const Sidebar = () => {
  const [activeTab, setActiveTab] = useState('agents');
  const [editingFilePath, setEditingFilePath] = useState(null);

  const handleFileSelect = (filePath) => {
    setEditingFilePath(filePath);
  };

  const handleCancelEdit = () => {
    setEditingFilePath(null);
  };

  const handleFileSaveSuccess = () => {
    setEditingFilePath(null);
  };

  return (
    <aside className="sidebar">
      <nav className="sidebar-nav">
        <ul>
          <li className={activeTab === 'agents' ? 'active' : ''}>
            <button onClick={() => { setActiveTab('agents'); setEditingFilePath(null); }}>Agents</button>
          </li>
          <li className={activeTab === 'files' ? 'active' : ''}>
            <button onClick={() => setActiveTab('files')}>Files</button>
          </li>
          <li className={activeTab === 'settings' ? 'active' : ''}>
            <button onClick={() => { setActiveTab('settings'); setEditingFilePath(null); }}>Settings</button>
          </li>
        </ul>
      </nav>

      <ModelSelector />

      <div className="sidebar-content">
        {activeTab === 'agents' && (
          <div className="sidebar-section">
            <h3>Agents</h3>
            <p>Create and monitor self-running agents.</p>
          </div>
        )}
        {activeTab === 'files' && (
          <div className="sidebar-section file-management-section">
            <h3>File Management</h3>
            {editingFilePath ? (
              <FileEditor
                filePath={editingFilePath}
                onCancel={handleCancelEdit}
                onSaveSuccess={handleFileSaveSuccess}
              />
            ) : (
              <FileBrowser onFileSelect={handleFileSelect} />
            )}
          </div>
        )}
        {activeTab === 'settings' && (
          <div className="sidebar-section">
            <h3>Settings</h3>
            <RoleSettings />
            <ToolSettings />
            <McpServerSettings />
          </div>
        )}
      </div>
    </aside>
  );
};

export default Sidebar;
