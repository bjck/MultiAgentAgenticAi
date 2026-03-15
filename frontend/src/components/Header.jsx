import React from 'react';
import { useTheme } from '../context/ThemeProvider';
import { useWebSocket } from '../context/WebSocketProvider';

const Header = ({ activeView, onChangeView }) => {
  const { theme, toggleTheme } = useTheme();
  const { sessionId } = useWebSocket();

  return (
    <header className="header">
      <div>
        <h1>MultiAgent Builder</h1>
        {sessionId && <small>Session: {sessionId}</small>}
      </div>
      <div className="header-actions">
        <div className="view-toggle" role="tablist" aria-label="View selector">
          <button
            type="button"
            className={activeView === 'agents' ? 'active' : ''}
            onClick={() => onChangeView?.('agents')}
            role="tab"
            aria-selected={activeView === 'agents'}
          >
            Agents
          </button>
          <button
            type="button"
            className={activeView === 'arxiv' ? 'active' : ''}
            onClick={() => onChangeView?.('arxiv')}
            role="tab"
            aria-selected={activeView === 'arxiv'}
          >
            Arxiv
          </button>
          <button
            type="button"
            className={activeView === 'dashboard' ? 'active' : ''}
            onClick={() => onChangeView?.('dashboard')}
            role="tab"
            aria-selected={activeView === 'dashboard'}
          >
            Insights
          </button>
        </div>
        <button onClick={toggleTheme}>
          Toggle to {theme === 'light' ? 'Dark' : 'Light'} Mode
        </button>
      </div>
    </header>
  );
};

export default Header;
