import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/ChatMessage.css';

const MAX_CHARS = 500; // Define maximum characters before truncation

const ChatMessage = ({ message }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [copied, setCopied] = useState(false); // State for copy feedback

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

  const copyToClipboard = () => {
    navigator.clipboard.writeText(fullContent)
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000); // Show "Copied!" for 2 seconds
      })
      .catch(err => {
        console.error('Failed to copy: ', err);
      });
  };

  return (
    <div className={`chat-message ${messageClass}`}>
      <div className="message-content">
        <ReactMarkdown>{displayContent}</ReactMarkdown>
        {showToggleButton && (
          <button className="toggle-button" onClick={() => setIsExpanded(!isExpanded)}>
            {isExpanded ? 'Show Less' : 'Show More'}
          </button>
        )}
        <button className="copy-button" onClick={copyToClipboard} title="Copy to clipboard">
          {copied ? 'Copied!' : '📋'} {/* Clipboard icon or "Copied!" text */}
        </button>
      </div>
    </div>
  );
};

export default ChatMessage;
