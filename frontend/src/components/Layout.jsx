import React, { useEffect, useState } from 'react';
import Header from './Header';
import Sidebar from './Sidebar';

const MIN_WIDTH = 220;
const MAX_WIDTH = 520;

const clamp = (value) => Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, value));

const Layout = ({ children }) => {
  const [sidebarWidth, setSidebarWidth] = useState(() => {
    const saved = Number(localStorage.getItem('sidebarWidth'));
    return Number.isFinite(saved) && saved > 0 ? clamp(saved) : 280;
  });
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    localStorage.setItem('sidebarWidth', String(sidebarWidth));
  }, [sidebarWidth]);

  useEffect(() => {
    if (!dragging) {
      return undefined;
    }

    const handleMove = (event) => {
      const next = clamp(event.clientX);
      setSidebarWidth(next);
    };

    const handleUp = () => {
      setDragging(false);
    };

    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);

    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
  }, [dragging]);

  return (
    <div className="App">
      <Header />
      <div className="main-container">
        <div className="sidebar-wrapper" style={{ width: sidebarWidth }}>
          <Sidebar />
          <div
            className="sidebar-resizer"
            onMouseDown={() => setDragging(true)}
            role="separator"
            aria-orientation="vertical"
            aria-label="Resize sidebar"
          />
        </div>
        <main className="content">
          {children}
        </main>
      </div>
    </div>
  );
};

export default Layout;
