import React, { useState } from 'react';
import '../styles/Sidebar.css';
import ModelSelector from './ModelSelector';
import FileBrowser from './FileBrowser';
import FileEditor from './FileEditor';
import SkillManager from './SkillManager';
import RoleSettings from './RoleSettings';

const Sidebar = () => {
  const [activeTab, setActiveTab] = useState('chat');
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
          <li className={activeTab === 'chat' ? 'active' : ''}>
            <button onClick={() => { setActiveTab('chat'); setEditingFilePath(null); }}>Chat</button>
          </li>
          <li className={activeTab === 'files' ? 'active' : ''}>
            <button onClick={() => setActiveTab('files')}>Files</button>
          </li>
          <li className={activeTab === 'skills' ? 'active' : ''}>
            <button onClick={() => { setActiveTab('skills'); setEditingFilePath(null); }}>Skills</button>
          </li>
          <li className={activeTab === 'settings' ? 'active' : ''}>
            <button onClick={() => { setActiveTab('settings'); setEditingFilePath(null); }}>Settings</button>
          </li>
        </ul>
      </nav>

      <ModelSelector />

      <div className="sidebar-content">
        {activeTab === 'chat' && (
          <div className="sidebar-section">
            <h3>Chat Options</h3>
            <p>Chat specific settings or information will go here.</p>
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
        {activeTab === 'skills' && (
          <div className="sidebar-section skill-management-section">
            <h3>Skill Management</h3>
            <SkillManager />
          </div>
        )}
        {activeTab === 'settings' && (
          <div className="sidebar-section">
            <h3>Settings</h3>
            <RoleSettings />
          </div>
        )}
      </div>
    </aside>
  );
};

export default Sidebar;
