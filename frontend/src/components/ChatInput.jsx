import React, { useState } from 'react';
import { useWebSocket } from '../context/WebSocketProvider';
import '../styles/ChatInput.css';

const ChatInput = () => {
  const [input, setInput] = useState('');
  const [mode, setMode] = useState('stream');
  const { sendMessage, sendMessageSync, requestPlan, clearChat } = useWebSocket();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!input.trim()) {
      return;
    }
    if (mode === 'plan') {
      await requestPlan(input);
    } else if (mode === 'sync') {
      await sendMessageSync(input);
    } else {
      await sendMessage(input);
    }
    setInput('');
  };

  return (
    <div className="chat-input-container">
      <form onSubmit={handleSubmit} className="chat-input-form">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type your message here..."
          rows="3"
          className="chat-textarea"
        />
        <div className="chat-input-controls">
          <label htmlFor="chat-mode" className="chat-mode-label">Mode</label>
          <select
            id="chat-mode"
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            className="chat-mode-select"
          >
            <option value="stream">Stream</option>
            <option value="sync">Sync</option>
            <option value="plan">Plan only</option>
          </select>
        </div>
        <div className="chat-input-actions">
          <button type="submit" className="send-button">Send</button>
          <button type="button" onClick={clearChat} className="clear-button">Clear Chat</button>
        </div>
      </form>
    </div>
  );
};

export default ChatInput;
