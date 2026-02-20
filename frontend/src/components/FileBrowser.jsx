import React, { useState, useEffect } from 'react';
import '../styles/FileBrowser.css';

const FileBrowser = ({ onFileSelect }) => {
  const [currentPath, setCurrentPath] = useState('');
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchFiles = async (path) => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`/api/files?path=${encodeURIComponent(path)}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      setFiles(data.entries || []);
      setCurrentPath(data.path ?? path);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFiles('');
  }, []);

  const handleItemClick = (item) => {
    if (item.directory) {
      fetchFiles(item.path);
    } else {
      onFileSelect(item.path);
    }
  };

  const handleBackClick = () => {
    if (!currentPath) {
      return;
    }
    const parentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
    fetchFiles(parentPath);
  };

  if (loading) return <div className="file-browser-container">Loading files...</div>;
  if (error) return <div className="file-browser-container error">Error: {error}</div>;

  return (
    <div className="file-browser-container">
      <div className="path-header">
        {currentPath && (
          <button onClick={handleBackClick} className="back-button">
            {'<- Back'}
          </button>
        )}
        <span>Current Path: {currentPath || '/'}</span>
      </div>
      <ul className="file-list">
        {files.length === 0 && <li className="empty-message">No files or directories found.</li>}
        {files.map((item) => (
          <li
            key={item.path}
            onClick={() => handleItemClick(item)}
            className={`file-item ${item.directory ? 'dir' : 'file'}`}
          >
            {item.directory ? '[DIR]' : '[FILE]'} {item.name}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default FileBrowser;
