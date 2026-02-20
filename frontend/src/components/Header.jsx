import React from 'react';
import { useTheme } from '../context/ThemeProvider';
import { useWebSocket } from '../context/WebSocketProvider';

const Header = () => {
  const { theme, toggleTheme } = useTheme();
  const { sessionId } = useWebSocket();

  return (
    <header className="header">
      <div>
        <h1>MultiAgent Builder</h1>
        {sessionId && <small>Session: {sessionId}</small>}
      </div>
      <div>
        <button onClick={toggleTheme}>
          Toggle to {theme === 'light' ? 'Dark' : 'Light'} Mode
        </button>
      </div>
    </header>
  );
};

export default Header;
