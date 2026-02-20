import React, { useEffect, useRef } from 'react';
import { useWebSocket } from '../context/WebSocketProvider';
import ChatMessage from './ChatMessage';
import ChatInput from './ChatInput';
import PlanPreview from './PlanPreview';
import '../styles/ChatContainer.css'; // We will create this CSS file next

const ChatContainer = () => {
  const { messages, plan } = useWebSocket();
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  return (
    <div className="chat-container">
      <div className="messages-list">
        {plan && <PlanPreview plan={plan} />}
        {messages.map((msg, index) => (
          <ChatMessage key={index} message={msg} />
        ))}
        <div ref={messagesEndRef} />
      </div>
      <ChatInput />
    </div>
  );
};

export default ChatContainer;
