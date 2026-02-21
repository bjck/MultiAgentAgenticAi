import React, { useEffect, useRef } from 'react';
import { useWebSocket } from '../context/WebSocketProvider';
import ChatMessage from './ChatMessage';
import ChatInput from './ChatInput';
import PlanPreview from './PlanPreview';
import '../styles/ChatContainer.css';

const ChatContainer = () => {
  const { messages, plan, isAgentWorking } = useWebSocket();
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isAgentWorking]); // Added isAgentWorking to dependencies to scroll when spinner appears/disappears

  return (
    <div className="chat-container">
      <div className="messages-list">
        {plan && <PlanPreview plan={plan} />}
        {messages.map((msg, index) => (
          <ChatMessage key={index} message={msg} />
        ))}
        {isAgentWorking && (
          <div className="agent-spinner-container">
            <div className="agent-spinner"></div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>
      <ChatInput />
    </div>
  );
};

export default ChatContainer;
