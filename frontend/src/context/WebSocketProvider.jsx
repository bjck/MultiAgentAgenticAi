import React, { createContext, useContext, useRef, useState } from 'react';

const WebSocketContext = createContext(null);

export const useWebSocket = () => useContext(WebSocketContext);

const formatPlan = (plan) => {
  if (!plan) return '';
  if (typeof plan === 'string') return plan;
  const tasks = Array.isArray(plan.tasks) ? plan.tasks : [];
  const lines = [`## Objective`, plan.objective || '', '', '## Tasks'];
  tasks.forEach((task, index) => {
    lines.push(`- **${index + 1}. ${task.role || 'role'}**: ${task.description || ''}`);
  });
  return lines.join('\n');
};

export const WebSocketProvider = ({ children }) => {
  const [messages, setMessages] = useState([]);
  const [plan, setPlan] = useState('');
  const [sessionId, setSessionId] = useState('');
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
    setPlan('');
    setSessionId('');
    taskBuffers.current.clear();
    closeSocket();
  };

  const handleStreamEvent = (event) => {
    const { type, data } = event;
    if (type === 'session') {
      setSessionId(data?.sessionId || '');
      return;
    }
    if (type === 'plan' || type === 'plan-update') {
      setPlan(formatPlan(data));
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
      return;
    }
    if (type === 'error') {
      appendMessage({ type: 'system', content: data?.message || 'Unknown error.' });
      setIsAgentWorking(false); // Agent finished working with an error
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
      if (data?.plan) {
        setPlan(formatPlan(data.plan));
      }
      if (data?.finalAnswer) {
        appendMessage({ type: 'agent', content: data.finalAnswer });
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
      if (data?.objective || data?.tasks) {
        setPlan(formatPlan({ objective: data.objective, tasks: data.tasks }));
      }
      setIsAgentWorking(false); // Agent finished working
    } catch (e) {
      appendMessage({ type: 'system', content: `Failed to plan: ${e.message}` });
      setIsAgentWorking(false); // Agent finished working with an error
    }
  };

  const clearChat = () => {
    setMessages([]);
    setPlan('');
    setSessionId('');
    taskBuffers.current.clear();
    closeSocket();
    setIsAgentWorking(false); // Ensure spinner is off when chat is cleared
  };

  const value = {
    messages,
    plan,
    sessionId,
    sendMessage,
    sendMessageSync,
    requestPlan,
    clearChat,
    selectedProvider,
    setSelectedProvider,
    selectedModel,
    setSelectedModel,
    isAgentWorking, // Expose isAgentWorking state
  };

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
};
