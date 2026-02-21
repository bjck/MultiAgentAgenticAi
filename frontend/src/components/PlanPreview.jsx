import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/PlanPreview.css'; // We will create this CSS file next

const buildTaskMarkdown = (plan) => {
  if (!plan) return '';
  const lines = [`## Objective`, plan.objective || '', '', '## Tasks'];
  const tasks = Array.isArray(plan.tasks) ? plan.tasks : [];
  if (tasks.length === 0) {
    lines.push('_No tasks generated._');
    return lines.join('\n');
  }
  tasks.forEach((task, index) => {
    lines.push(`- **${index + 1}. ${task.role || 'role'}**: ${task.description || ''}`);
    if (task.expectedOutput) {
      lines.push(`  - Expected output: ${task.expectedOutput}`);
    }
  });
  return lines.join('\n');
};

const buildFindingsMarkdown = (findings) => {
  if (!Array.isArray(findings) || findings.length === 0) {
    return '_No discovery findings available._';
  }
  return findings
    .map((finding, index) => {
      const role = finding?.role || 'discovery';
      const taskId = finding?.taskId ? ` (${finding.taskId})` : '';
      const output = finding?.output || '';
      return `### ${index + 1}. ${role}${taskId}\n\n${output}`;
    })
    .join('\n\n');
};

const PlanPreview = ({ plan, onExecute, onRevise, isAgentWorking }) => {
  const [feedback, setFeedback] = useState('');
  if (!plan) {
    return null;
  }

  return (
    <div className="plan-preview-container">
      <div className="plan-header">
        <h3>Plan Draft</h3>
        {plan.status && <span className="plan-status">{plan.status}</span>}
      </div>
      <div className="plan-content">
        <div className="plan-section">
          <h4>Discovery Findings</h4>
          <ReactMarkdown>{buildFindingsMarkdown(plan.findings)}</ReactMarkdown>
        </div>
        <div className="plan-section">
          <ReactMarkdown>{buildTaskMarkdown(plan)}</ReactMarkdown>
        </div>
      </div>
      <div className="plan-actions">
        <textarea
          value={feedback}
          onChange={(e) => setFeedback(e.target.value)}
          placeholder="Optional feedback before execution..."
          rows="2"
          className="plan-feedback-input"
        />
        <div className="plan-action-buttons">
          <button
            type="button"
            className="plan-revise-button"
            onClick={() => onRevise?.(feedback)}
            disabled={isAgentWorking}
          >
            Revise Plan
          </button>
          <button
            type="button"
            className="plan-approve-button"
            onClick={() => onExecute?.(plan.planId, feedback, true)}
            disabled={!plan.planId || isAgentWorking}
          >
            Approve & Run
          </button>
        </div>
      </div>
    </div>
  );
};

export default PlanPreview;
