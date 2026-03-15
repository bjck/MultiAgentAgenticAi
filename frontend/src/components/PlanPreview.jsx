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

const renderSkillPlans = (skillPlans = []) => {
  if (!Array.isArray(skillPlans) || skillPlans.length === 0) {
    return <p className="plan-muted">No skill plans available.</p>;
  }

  return (
    <div className="plan-skill-list">
      {skillPlans.map((plan) => (
        <details className="plan-skill-item" key={`${plan.taskId}-${plan.role}`}>
          <summary>
            <span className="plan-skill-task">{plan.taskId || 'task'}</span>
            <span className="plan-skill-role">{plan.role || 'role'}</span>
            <span className="plan-skill-count">
              {Array.isArray(plan.skills) ? plan.skills.length : 0}/{plan.budget ?? 0} skills
            </span>
          </summary>
          <div className="plan-skill-body">
            {plan.rationale && <p className="plan-skill-rationale">{plan.rationale}</p>}
            <div className="plan-skill-chips">
              {(plan.skills || []).map((skill) => (
                <div className="plan-skill-chip" key={`${plan.taskId}-${skill.name}`}>
                  <span>{skill.name}</span>
                  {skill.description && <small>{skill.description}</small>}
                </div>
              ))}
            </div>
          </div>
        </details>
      ))}
    </div>
  );
};

const PlanPreview = ({ plan, onExecute, onRevise, onRunDirect, isAgentWorking }) => {
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
        <div className="plan-section">
          <h4>Skill Plans</h4>
          {renderSkillPlans(plan.skillPlans)}
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
            className="plan-skip-button"
            onClick={() => onRunDirect?.()}
            disabled={isAgentWorking}
          >
            Run directly (skip plan)
          </button>
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
