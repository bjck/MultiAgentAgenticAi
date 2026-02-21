import React, { createContext, useContext, useRef, useState } from 'react';

const WebSocketContext = createContext(null);

export const useWebSocket = () => useContext(WebSocketContext);

const normalizePlanPayload = (payload) => {
  if (!payload) return null;
  const plan = payload.plan ? payload.plan : payload;
  const tasks = Array.isArray(plan.tasks) ? plan.tasks : [];
  const findings = Array.isArray(payload.findings) ? payload.findings : [];
  return {
    objective: plan.objective || '',
    tasks,
    findings,
    planId: payload.planId || payload.plan_id || '',
    sessionId: payload.sessionId || payload.session_id || '',
    status: payload.status || '',
  };
};

export const WebSocketProvider = ({ children }) => {
  const [messages, setMessages] = useState([]);
  const [plan, setPlan] = useState(null);
  const [sessionId, setSessionId] = useState('');
  const [lastPrompt, setLastPrompt] = useState('');
  const [selectedProvider, setSelectedProvider] = useState('GOOGLE');
  const [selectedModel, setSelectedModel] = useState('');
  const [isAgentWorking, setIsAgentWorking] = useState(false); // New state for agent activity
  const wsRef = useRef(null);
  const runIdRef = useRef(null);
  const taskBuffers = useRef(new Map());

  const appendMessage = (message) => {
    setMessages((prev) => [...prev, message]);
  };

  const closeSocket = () => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
  };

  const resetForNewRequest = () => {
    setPlan(null);
    setSessionId('');
    taskBuffers.current.clear();
    closeSocket();
    runIdRef.current = null; // Clear runId on new request
  };

  const handleStreamEvent = (event) => {
    const { type, data } = event;
    if (type === 'session') {
      setSessionId(data?.sessionId || '');
      return;
    }
    if (type === 'plan' || type === 'plan-update') {
      const normalized = normalizePlanPayload(data);
      setPlan(normalized);
      if (normalized?.sessionId) {
        setSessionId(normalized.sessionId);
      }
      return;
    }
    if (type === 'task-output') {
      const taskId = data?.taskId;
      if (!taskId) return;
      const buffer = taskBuffers.current.get(taskId) || { chunks: [], role: data?.role };
      buffer.chunks[data.sequence || 0] = data.chunk || '';
      buffer.role = data?.role || buffer.role;
      taskBuffers.current.set(taskId, buffer);
      if (data.done) {
        const text = buffer.chunks.join('');
        const prefix = buffer.role ? `**${buffer.role}**\n\n` : '';
        appendMessage({ type: 'agent', content: `${prefix}${text}` });
        taskBuffers.current.delete(taskId);
      }
      return;
    }
    if (type === 'final') {
      const text = data?.finalAnswer || '';
      if (text) {
        appendMessage({ type: 'agent', content: text });
      }
      setIsAgentWorking(false); // Agent finished working
      runIdRef.current = null; // Clear runId on completion
      return;
    }
    if (type === 'run-complete') {
      if (data?.status) {
        setPlan((prev) => (prev ? { ...prev, status: data.status } : prev));
      }
      setIsAgentWorking(false);
      runIdRef.current = null; // Clear runId on completion
      return;
    }
    if (type === 'run-cancel') {
      setPlan((prev) => (prev ? { ...prev, status: 'CANCELLED' } : prev));
      appendMessage({ type: 'system', content: 'Run cancelled.' });
      setIsAgentWorking(false);
      runIdRef.current = null;
      closeSocket();
      return;
    }
    if (type === 'error') {
      appendMessage({ type: 'system', content: data?.message || 'Unknown error.' });
      setIsAgentWorking(false); // Agent finished working with an error
      runIdRef.current = null; // Clear runId on error
    }
  };

  const connectToRun = (runId) => {
    closeSocket();
    runIdRef.current = runId;
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const wsUrl = `${protocol}://${window.location.host}/ws/stream?runId=${encodeURIComponent(runId)}`;
    wsRef.current = new WebSocket(wsUrl);

    wsRef.current.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        handleStreamEvent(payload);
      } catch (e) {
        console.error('Failed to parse stream event:', e);
      }
    };

    wsRef.current.onclose = () => {
      // no-op
    };

    wsRef.current.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  };

  const sendMessage = async (message) => {
    const trimmed = message.trim();
    if (!trimmed) return;
    appendMessage({ type: 'user', content: trimmed });
    setLastPrompt(trimmed);
    resetForNewRequest();
    setIsAgentWorking(true); // Agent starts working

    const payload = {
      message: trimmed,
      provider: selectedProvider || null,
      model: selectedModel || null,
    };

    try {
      const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        appendMessage({ type: 'system', content: `Failed to start run (${response.status}).` });
        setIsAgentWorking(false); // Agent finished working with an error
        return;
      }

      const data = await response.json();
      if (data?.runId) {
        connectToRun(data.runId);
      } else {
        appendMessage({ type: 'system', content: 'Missing run ID from server.' });
        setIsAgentWorking(false); // Agent finished working with an error
      }
    } catch (e) {
      appendMessage({ type: 'system', content: `Failed to start run: ${e.message}` });
      setIsAgentWorking(false); // Agent finished working with an error
    }
  };

  const sendMessageSync = async (message) => {
    const trimmed = message.trim();
    if (!trimmed) return;
    appendMessage({ type: 'user', content: trimmed });
    setLastPrompt(trimmed);
    resetForNewRequest();
    setIsAgentWorking(true); // Agent starts working

    const payload = {
      message: trimmed,
      provider: selectedProvider || null,
      model: selectedModel || null,
    };

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        appendMessage({ type: 'system', content: `Failed to run (${response.status}).` });
        setIsAgentWorking(false); // Agent finished working with an error
        return;
      }
      const data = await response.json();
      const normalized = normalizePlanPayload(data);
      if (normalized) {
        setPlan(normalized);
        if (normalized.sessionId) {
          setSessionId(normalized.sessionId);
        }
      }
      setIsAgentWorking(false); // Agent finished working
    } catch (e) {
      appendMessage({ type: 'system', content: `Failed to run: ${e.message}` });
      setIsAgentWorking(false); // Agent finished working with an error
    }
  };

  const requestPlan = async (message) => {
    const trimmed = message.trim();
    if (!trimmed) return;
    appendMessage({ type: 'user', content: trimmed });
    setLastPrompt(trimmed);
    resetForNewRequest();
    setIsAgentWorking(true); // Agent starts working

    const payload = {
      message: trimmed,
      provider: selectedProvider || null,
      model: selectedModel || null,
    };

    try {
      const response = await fetch('/api/chat/plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        appendMessage({ type: 'system', content: `Failed to plan (${response.status}).` });
        setIsAgentWorking(false); // Agent finished working with an error
        return;
      }
      const data = await response.json();
      const normalized = normalizePlanPayload(data);
      if (normalized) {
        setPlan(normalized);
        if (normalized.sessionId) {
          setSessionId(normalized.sessionId);
        }
      }
      setIsAgentWorking(false); // Agent finished working
    } catch (e) {
      appendMessage({ type: 'system', content: `Failed to plan: ${e.message}` });
      setIsAgentWorking(false); // Agent finished working with an error
    }
  };

  const clearChat = () => {
    setMessages([]);
    setPlan(null);
    setSessionId('');
    taskBuffers.current.clear();
    closeSocket();
    runIdRef.current = null; // Clear runId when chat is cleared
    setIsAgentWorking(false); // Ensure spinner is off when chat is cleared
  };

  const executePlan = async (planId, feedback, useStreaming = true) => {
    if (!planId) {
      appendMessage({ type: 'system', content: 'Missing plan ID for execution.' });
      return;
    }
    setPlan((prev) => (prev ? { ...prev, status: 'EXECUTING' } : prev));
    taskBuffers.current.clear();
    closeSocket();
    setIsAgentWorking(true);
    appendMessage({ type: 'system', content: 'Executing approved plan...' });

    const payload = {
      planId,
      feedback: feedback || null,
      provider: selectedProvider || null,
      model: selectedModel || null,
    };

    if (useStreaming) {
      try {
        const response = await fetch('/api/chat/execute/stream', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        if (!response.ok) {
          appendMessage({ type: 'system', content: `Failed to execute (${response.status}).` });
          setIsAgentWorking(false);
          return;
        }
        const data = await response.json();
        if (data?.runId) {
          connectToRun(data.runId);
        } else {
          appendMessage({ type: 'system', content: 'Missing run ID from server.' });
          setIsAgentWorking(false);
        }
      } catch (e) {
        appendMessage({ type: 'system', content: `Failed to execute: ${e.message}` });
        setIsAgentWorking(false);
      }
      return;
    }

    try {
      const response = await fetch('/api/chat/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        appendMessage({ type: 'system', content: `Failed to execute (${response.status}).` });
        setIsAgentWorking(false);
        return;
      }
      const data = await response.json();
      if (data?.finalAnswer) {
        appendMessage({ type: 'agent', content: data.finalAnswer });
      }
      setPlan((prev) => (prev ? { ...prev, status: 'COMPLETED' } : prev));
      setIsAgentWorking(false);
    } catch (e) {
      appendMessage({ type: 'system', content: `Failed to execute: ${e.message}` });
      setIsAgentWorking(false);
    }
  };

  const requestPlanRevision = async (feedback) => {
    if (!lastPrompt) {
      appendMessage({ type: 'system', content: 'Missing previous request to revise.' });
      return;
    }
    const note = feedback && feedback.trim()
      ? `\n\nUser feedback to revise discovery/plan:\n${feedback.trim()}`
      : '';
    const combined = `Original request:\n${lastPrompt}${note}`;
    await requestPlan(combined);
  };

  const cancelAgentRun = async () => {
    if (!runIdRef.current) {
      appendMessage({ type: 'system', content: 'No active run to cancel.' });
      return;
    }

    setIsAgentWorking(false); // Optimistically set to false
    appendMessage({ type: 'system', content: 'Attempting to cancel agent run...' });

    try {
      const response = await fetch('/api/chat/cancel', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ runId: runIdRef.current }),
      });

      const data = await response.json();

      if (response.ok && data.status === 'success') {
        appendMessage({ type: 'system', content: 'Agent run canceled successfully.' });
        closeSocket(); // Close the WebSocket connection for the canceled run
        runIdRef.current = null; // Clear the runId
        setPlan(null); // Clear the plan as it's no longer relevant
      } else {
        appendMessage({ type: 'system', content: `Failed to cancel run: ${data.message || 'Unknown error.'}` });
        // If cancellation failed, the agent might still be working, so revert isAgentWorking
        setIsAgentWorking(true);
      }
    } catch (e) {
      appendMessage({ type: 'system', content: `Error during cancellation: ${e.message}` });
      // If an error occurred, the agent might still be working, so revert isAgentWorking
      setIsAgentWorking(true);
    }
  };

  const value = {
    messages,
    plan,
    sessionId,
    sendMessage,
    sendMessageSync,
    requestPlan,
    requestPlanRevision,
    executePlan,
    clearChat,
    selectedProvider,
    setSelectedProvider,
    selectedModel,
    setSelectedModel,
    isAgentWorking, // Expose isAgentWorking state
    cancelAgentRun, // Expose cancelAgentRun function
    runId: runIdRef.current, // Expose runId for potential use
  };

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
};
