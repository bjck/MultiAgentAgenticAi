import React, { useEffect, useMemo, useState } from 'react';
import '../styles/SkillManager.css';

const SkillManager = () => {
  const [skillsData, setSkillsData] = useState(null);
  const [scope, setScope] = useState('worker-defaults');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ name: '', description: '', instructions: '' });
  const [editingIndex, setEditingIndex] = useState(null);
  const [editForm, setEditForm] = useState({ name: '', description: '', instructions: '' });

  useEffect(() => {
    const fetchSkills = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch('/api/config/skills');
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        setSkillsData(data);
        if (data.workerRoles && data.workerRoles.length > 0 && scope.startsWith('worker:')) {
          const role = scope.split(':')[1];
          if (!data.workerRoles.includes(role)) {
            setScope('worker-defaults');
          }
        }
      } catch (e) {
        setError(e.message);
      } finally {
        setLoading(false);
      }
    };

    fetchSkills();
  }, []);

  const availableScopes = useMemo(() => {
    const scopes = [
      { key: 'orchestrator', label: 'Orchestrator' },
      { key: 'synthesis', label: 'Synthesis' },
      { key: 'worker-defaults', label: 'Worker Defaults' },
    ];
    if (skillsData?.workerRoles) {
      skillsData.workerRoles.forEach((role) => {
        scopes.push({ key: `worker:${role}`, label: `Worker: ${role}` });
      });
    }
    return scopes;
  }, [skillsData]);

  const currentSkills = useMemo(() => {
    if (!skillsData) return [];
    if (scope === 'orchestrator') return skillsData.orchestrator || [];
    if (scope === 'synthesis') return skillsData.synthesis || [];
    if (scope === 'worker-defaults') return skillsData.workerDefaults || [];
    if (scope.startsWith('worker:')) {
      const role = scope.split(':')[1];
      return skillsData.workers?.[role] || [];
    }
    return [];
  }, [skillsData, scope]);

  const updateSkills = async (updatedSkills) => {
    if (!skillsData) return;
    setSaving(true);
    setError(null);
    try {
      let endpoint = '';
      if (scope === 'orchestrator') endpoint = '/api/config/skills/orchestrator';
      else if (scope === 'synthesis') endpoint = '/api/config/skills/synthesis';
      else if (scope === 'worker-defaults') endpoint = '/api/config/skills/worker-defaults';
      else if (scope.startsWith('worker:')) {
        const role = scope.split(':')[1];
        endpoint = `/api/config/skills/workers/${encodeURIComponent(role)}`;
      }

      const response = await fetch(endpoint, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedSkills),
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const updated = await response.json();
      setSkillsData((prev) => {
        if (!prev) return prev;
        const next = { ...prev };
        if (scope === 'orchestrator') next.orchestrator = updated;
        else if (scope === 'synthesis') next.synthesis = updated;
        else if (scope === 'worker-defaults') next.workerDefaults = updated;
        else if (scope.startsWith('worker:')) {
          const role = scope.split(':')[1];
          next.workers = { ...(prev.workers || {}), [role]: updated };
        }
        return next;
      });
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  const handleAddSkill = async (event) => {
    event.preventDefault();
    if (!form.name.trim()) return;
    const nextSkill = {
      name: form.name.trim(),
      description: form.description.trim(),
      instructions: form.instructions.trim(),
    };
    const updatedSkills = [...currentSkills, nextSkill];
    await updateSkills(updatedSkills);
    setForm({ name: '', description: '', instructions: '' });
  };

  const handleEditSkill = (index) => {
    setEditingIndex(index);
    const skill = currentSkills[index];
    setEditForm({
      name: skill?.name || '',
      description: skill?.description || '',
      instructions: skill?.instructions || '',
    });
  };

  const handleEditSave = async (event) => {
    event.preventDefault();
    if (editingIndex === null) return;
    const updatedSkills = currentSkills.map((skill, idx) => {
      if (idx !== editingIndex) return skill;
      return {
        name: editForm.name.trim(),
        description: editForm.description.trim(),
        instructions: editForm.instructions.trim(),
      };
    });
    await updateSkills(updatedSkills);
    setEditingIndex(null);
  };

  const handleDelete = async (index) => {
    const updatedSkills = currentSkills.filter((_, idx) => idx !== index);
    await updateSkills(updatedSkills);
    if (editingIndex === index) {
      setEditingIndex(null);
    }
  };

  if (loading) {
    return <div className="skill-manager-container">Loading skills...</div>;
  }

  if (error) {
    return <div className="skill-manager-container error">Error: {error}</div>;
  }

  return (
    <div className="skill-manager-container">
      <h3>Skills</h3>

      <label htmlFor="skill-scope">Scope</label>
      <select
        id="skill-scope"
        value={scope}
        onChange={(event) => {
          setScope(event.target.value);
          setEditingIndex(null);
        }}
      >
        {availableScopes.map((s) => (
          <option key={s.key} value={s.key}>
            {s.label}
          </option>
        ))}
      </select>

      <form className="skill-form" onSubmit={handleAddSkill}>
        <input
          type="text"
          placeholder="Skill name"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          disabled={saving}
        />
        <input
          type="text"
          placeholder="Short description"
          value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })}
          disabled={saving}
        />
        <textarea
          placeholder="Instructions"
          value={form.instructions}
          onChange={(event) => setForm({ ...form, instructions: event.target.value })}
          disabled={saving}
        />
        <button type="submit" disabled={saving || !form.name.trim()}>
          {saving ? 'Saving...' : 'Add Skill'}
        </button>
      </form>

      {currentSkills.length === 0 && <div className="empty-message">No skills configured.</div>}

      <ul className="skill-list">
        {currentSkills.map((skill, index) => (
          <li key={`${skill.name}-${index}`} className="skill-item">
            {editingIndex === index ? (
              <form className="edit-skill-form" onSubmit={handleEditSave}>
                <input
                  type="text"
                  value={editForm.name}
                  onChange={(event) => setEditForm({ ...editForm, name: event.target.value })}
                />
                <input
                  type="text"
                  value={editForm.description}
                  onChange={(event) => setEditForm({ ...editForm, description: event.target.value })}
                />
                <textarea
                  value={editForm.instructions}
                  onChange={(event) => setEditForm({ ...editForm, instructions: event.target.value })}
                />
                <div className="edit-actions">
                  <button type="submit" disabled={saving}>Save</button>
                  <button type="button" className="cancel-button" onClick={() => setEditingIndex(null)}>Cancel</button>
                </div>
              </form>
            ) : (
              <>
                <div className="skill-details">
                  <h4>{skill.name}</h4>
                  {skill.description && <p>{skill.description}</p>}
                  {skill.instructions && <p>{skill.instructions}</p>}
                </div>
                <div className="skill-actions">
                  <button type="button" className="edit-button" onClick={() => handleEditSkill(index)}>Edit</button>
                  <button type="button" className="delete-button" onClick={() => handleDelete(index)}>Delete</button>
                </div>
              </>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default SkillManager;
