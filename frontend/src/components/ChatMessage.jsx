import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/ChatMessage.css';

const MAX_CHARS = 500; // Define maximum characters before truncation

const ChatMessage = ({ message }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const resolved = typeof message === 'string' ? { type: 'agent', content: message } : message || {};
  const messageType = resolved.type || 'agent';
  const messageClass = messageType === 'user'
    ? 'user-message'
    : messageType === 'system'
      ? 'system-message'
      : 'agent-message';

  const fullContent = resolved.content || '';
  const showToggleButton = fullContent.length > MAX_CHARS;
  const displayContent = showToggleButton && !isExpanded
    ? fullContent.substring(0, MAX_CHARS) + '... ' // Add space before button
    : fullContent;

  return (
    <div className={`chat-message ${messageClass}`}>
      <div className="message-content">
        <ReactMarkdown>{displayContent}</ReactMarkdown>
        {showToggleButton && (
          <button className="toggle-button" onClick={() => setIsExpanded(!isExpanded)}>
            {isExpanded ? 'Show Less' : 'Show More'}
          </button>
        )}
      </div>
    </div>
  );
};

export default ChatMessage;
