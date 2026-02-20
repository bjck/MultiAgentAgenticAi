import React from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/ChatMessage.css';

const ChatMessage = ({ message }) => {
  const resolved = typeof message === 'string' ? { type: 'agent', content: message } : message || {};
  const messageType = resolved.type || 'agent';
  const messageClass = messageType === 'user'
    ? 'user-message'
    : messageType === 'system'
      ? 'system-message'
      : 'agent-message';

  return (
    <div className={`chat-message ${messageClass}`}>
      <div className="message-content">
        <ReactMarkdown>{resolved.content || ''}</ReactMarkdown>
      </div>
    </div>
  );
};

export default ChatMessage;
