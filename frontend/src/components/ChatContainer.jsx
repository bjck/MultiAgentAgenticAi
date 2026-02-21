import React, { useEffect, useRef } from 'react';
import { useWebSocket } from '../context/WebSocketProvider';
import ChatMessage from './ChatMessage';
import ChatInput from './ChatInput';
import PlanPreview from './PlanPreview';
import '../styles/ChatContainer.css';

const ChatContainer = () => {
  const { messages, plan, isAgentWorking, executePlan, requestPlanRevision, cancelAgentRun } = useWebSocket();
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
        {plan && (
          <PlanPreview
            plan={plan}
            onExecute={executePlan}
            onRevise={requestPlanRevision}
            isAgentWorking={isAgentWorking}
          />
        )}
        {messages.map((msg, index) => (
          <ChatMessage key={index} message={msg} />
        ))}
        {isAgentWorking && (
          <div className="agent-spinner-container">
            <div className="agent-spinner"></div>
            <button
              onClick={cancelAgentRun}
              className="cancel-button"
            >
              Cancel
            </button>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>
      <ChatInput isAgentWorking={isAgentWorking} />
    </div>
  );
};

export default ChatContainer;
