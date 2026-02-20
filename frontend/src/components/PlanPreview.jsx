import React from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/PlanPreview.css'; // We will create this CSS file next

const PlanPreview = ({ plan }) => {
  if (!plan) {
    return null;
  }

  return (
    <div className="plan-preview-container">
      <h3>Agent's Plan</h3>
      <div className="plan-content">
        <ReactMarkdown>{plan}</ReactMarkdown>
      </div>
    </div>
  );
};

export default PlanPreview;
