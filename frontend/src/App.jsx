import React, { useEffect, useState } from 'react';
import Layout from './components/Layout';
import Dashboard from './components/Dashboard';
import AgentList from './components/AgentList';
import AgentDetail from './components/AgentDetail';
import AgentCreateForm from './components/AgentCreateForm';
import ArxivView from './components/ArxivView';
import './styles/App.css';

const VALID_VIEWS = ['agents', 'arxiv', 'dashboard'];

function viewFromHash() {
  if (typeof window === 'undefined') return 'agents';
  const hash = window.location.hash.slice(1).toLowerCase();
  return VALID_VIEWS.includes(hash) ? hash : 'agents';
}

function App() {
  const [activeView, setActiveView] = useState(viewFromHash);
  const [selectedAgentId, setSelectedAgentId] = useState(null);
  const [agentRefreshSignal, setAgentRefreshSignal] = useState(0);

  // Sync activeView from URL hash so browser back/forward switches tabs
  useEffect(() => {
    const onHashChange = () => setActiveView(viewFromHash());
    window.addEventListener('hashchange', onHashChange);
    // Ensure initial entry has a hash so Back has a target after first navigation
    if (!window.location.hash.slice(1) && viewFromHash() === 'agents') {
      window.history.replaceState(null, '', '#agents');
    }
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  const changeView = (view) => {
    const next = VALID_VIEWS.includes(view) ? view : 'agents';
    setActiveView(next);
    if (window.location.hash.slice(1) !== next) {
      window.location.hash = next;
    }
  };

  const handleAgentCreated = (agent) => {
    setAgentRefreshSignal((v) => v + 1);
    if (agent?.id) {
      setSelectedAgentId(agent.id);
    }
  };

  const handleAgentDeleted = () => {
    setSelectedAgentId(null);
    setAgentRefreshSignal((v) => v + 1);
  };

  return (
    <Layout activeView={activeView} onChangeView={changeView}>
      {activeView === 'agents' && (
        <div className="dashboard">
          <div className="dashboard-hero">
            <div className="dashboard-title">
              <p className="dashboard-eyebrow">Autonomous Agents</p>
              <h2>Self-running Agent Console</h2>
              <p className="dashboard-subtitle">
                Describe agents in natural language, schedule them, and let them run on their own.
              </p>
            </div>
          </div>
          <div className="dashboard-grid">
            <div>
              <AgentCreateForm onCreated={handleAgentCreated} />
              <AgentList onSelect={setSelectedAgentId} refreshSignal={agentRefreshSignal} />
            </div>
            <AgentDetail agentId={selectedAgentId} onDeleted={handleAgentDeleted} />
          </div>
        </div>
      )}
      {activeView === 'arxiv' && <ArxivView />}
      {activeView === 'dashboard' && <Dashboard />}
    </Layout>
  );
}

export default App;
