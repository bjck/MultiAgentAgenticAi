const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");
const chatStatus = document.getElementById("chat-status");
const planOutput = document.getElementById("plan-output");
const workerOutput = document.getElementById("worker-output");
const finalOutput = document.getElementById("final-output");
const clearChat = document.getElementById("clear-chat");
const connectionLabel = document.getElementById("connection-label");
const previewPlanBtn = document.getElementById("preview-plan");

const fileList = document.getElementById("file-list");
const pathDisplay = document.getElementById("path-display");
const upDir = document.getElementById("up-dir");
const refreshFiles = document.getElementById("refresh-files");
const newFileName = document.getElementById("new-file-name");
const createFile = document.getElementById("create-file");
const editor = document.getElementById("editor");
const editorPath = document.getElementById("editor-path");
const saveFile = document.getElementById("save-file");

const toolButtons = document.querySelectorAll(".floating-tools .tool-button");
const sidePanels = document.querySelectorAll(".side-panel");
const drawer = document.getElementById("drawer");
const drawerOverlay = document.getElementById("drawer-overlay");
const drawerClose = document.getElementById("drawer-close");

// Skills panel elements
const refreshSkills = document.getElementById("refresh-skills");
const workerRoleSelect = document.getElementById("worker-role");
const skillsTabs = document.querySelectorAll(".skills-tabs .tab");
const skillsPanels = document.querySelectorAll(".skills-panel");

let currentPath = "";
let selectedFile = "";
let skillsData = null;
let currentWorkerRole = "";
let currentPlan = null;
let currentMessage = "";

const setStatus = (message) => {
  chatStatus.textContent = message;
};

const setConnection = (message) => {
  connectionLabel.textContent = message;
};

const renderPlan = (plan) => {
  if (!plan || !plan.tasks || plan.tasks.length === 0) {
    planOutput.textContent = "No plan returned.";
    planOutput.classList.add("empty");
    return;
  }
  planOutput.classList.remove("empty");
  planOutput.innerHTML = "";
  const objective = document.createElement("div");
  objective.textContent = plan.objective || "Objective not provided.";
  objective.classList.add("task");
  planOutput.appendChild(objective);
  plan.tasks.forEach((task) => {
    const row = document.createElement("div");
    row.classList.add("task");
    const role = document.createElement("span");
    role.classList.add("role");
    role.textContent = task.role || "general";
    const desc = document.createElement("span");
    desc.textContent = task.description || "";
    row.appendChild(role);
    row.appendChild(desc);
    planOutput.appendChild(row);
  });
};

const renderWorkers = (results) => {
  if (!results || results.length === 0) {
    workerOutput.textContent = "No worker output returned.";
    workerOutput.classList.add("empty");
    return;
  }
  workerOutput.classList.remove("empty");
  workerOutput.innerHTML = "";
  results.forEach((result) => {
    const card = document.createElement("div");
    card.classList.add("worker-card");
    const heading = document.createElement("div");
    heading.classList.add("worker-heading");
    const role = document.createElement("span");
    role.classList.add("role");
    role.textContent = result.role || "worker";
    const taskId = document.createElement("span");
    taskId.classList.add("task-id");
    taskId.textContent = result.taskId || "";
    heading.appendChild(role);
    heading.appendChild(taskId);
    const body = document.createElement("div");
    body.classList.add("markdown-content");
    body.innerHTML = renderMarkdown(result.output || "");
    card.appendChild(heading);
    card.appendChild(body);
    workerOutput.appendChild(card);
  });
};

const renderFinal = (finalAnswer) => {
  if (!finalAnswer) {
    finalOutput.textContent = "No response returned.";
    finalOutput.classList.add("empty");
    return;
  }
  finalOutput.classList.remove("empty");
  finalOutput.classList.add("markdown-content");
  finalOutput.innerHTML = renderMarkdown(finalAnswer);
};

// Markdown rendering helper
const renderMarkdown = (text) => {
  if (!text) return "";
  try {
    // Configure marked for safe rendering
    marked.setOptions({
      breaks: true,
      gfm: true,
    });
    return marked.parse(text);
  } catch (e) {
    console.error("Markdown parsing error:", e);
    return escapeHtml(text);
  }
};

const escapeHtml = (text) => {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
};

const setActiveSidePanel = (target) => {
  toolButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.side === target);
  });
  sidePanels.forEach((panel) => {
    panel.classList.toggle("active", panel.id === `side-${target}`);
  });
};

const openDrawer = (target) => {
  setActiveSidePanel(target);
  drawer.classList.add("open");
  drawerOverlay.classList.add("open");
  drawer.setAttribute("aria-hidden", "false");
  drawerOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("drawer-open");
};

const closeDrawer = () => {
  drawer.classList.remove("open");
  drawerOverlay.classList.remove("open");
  drawer.setAttribute("aria-hidden", "true");
  drawerOverlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("drawer-open");
};

const sendChat = async (message) => {
  setStatus("Running agents...");
  try {
    const response = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message }),
    });
    if (!response.ok) {
      throw new Error(`Chat failed with ${response.status}`);
    }
    const data = await response.json();
    renderPlan(data.plan);
    renderWorkers(data.workerResults);
    renderFinal(data.finalAnswer);
    setConnection("Last run: " + new Date(data.createdAt).toLocaleTimeString());
    setStatus("Agents complete.");
  } catch (error) {
    setStatus("Failed to run agents.");
    finalOutput.classList.remove("markdown-content");
    finalOutput.textContent = error.message;
    finalOutput.classList.remove("empty");
  }
};

// Plan preview functionality
const previewPlan = async (message) => {
  setStatus("Generating plan preview...");
  try {
    const response = await fetch("/api/chat/plan", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message }),
    });
    if (!response.ok) {
      throw new Error(`Plan preview failed with ${response.status}`);
    }
    const data = await response.json();
    currentPlan = data;
    currentMessage = message;
    showPlanPreviewModal(data);
    setStatus("Plan ready for review.");
  } catch (error) {
    setStatus("Failed to generate plan.");
    console.error(error);
  }
};

const showPlanPreviewModal = (plan) => {
  const overlay = document.createElement("div");
  overlay.classList.add("modal-overlay");

  let tasksHtml = "";
  if (plan.tasks && plan.tasks.length > 0) {
    tasksHtml = plan.tasks.map((task, index) => `
      <div class="plan-task">
        <div class="plan-task-header">
          <span class="task-number">#${index + 1}</span>
          <span class="role">${task.role || "general"}</span>
          <span class="task-id">${task.id || ""}</span>
        </div>
        <div class="plan-task-description">${escapeHtml(task.description || "")}</div>
        <div class="plan-task-expected">
          <strong>Expected output:</strong> ${escapeHtml(task.expectedOutput || "")}
        </div>
      </div>
    `).join("");
  } else {
    tasksHtml = '<div class="empty">No tasks in plan.</div>';
  }

  overlay.innerHTML = `
    <div class="modal plan-preview-modal">
      <h3>Plan Preview</h3>
      <div class="plan-objective">
        <strong>Objective:</strong> ${escapeHtml(plan.objective || "No objective specified")}
      </div>
      <div class="plan-tasks-header">
        <strong>Tasks (${plan.tasks?.length || 0})</strong>
      </div>
      <div class="plan-tasks-list">
        ${tasksHtml}
      </div>
      <div class="modal-actions">
        <button class="cancel">Cancel</button>
        <button class="execute">Execute Plan</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  overlay.querySelector(".cancel").addEventListener("click", () => {
    overlay.remove();
    setStatus("Plan cancelled.");
  });

  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) {
      overlay.remove();
      setStatus("Plan cancelled.");
    }
  });

  overlay.querySelector(".execute").addEventListener("click", () => {
    overlay.remove();
    renderPlan({ objective: plan.objective, tasks: plan.tasks });
    sendChat(currentMessage);
  });
};

chatForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const message = chatInput.value.trim();
  if (!message) {
    setStatus("Enter a message first.");
    return;
  }
  sendChat(message);
});

previewPlanBtn.addEventListener("click", () => {
  const message = chatInput.value.trim();
  if (!message) {
    setStatus("Enter a message first.");
    return;
  }
  previewPlan(message);
});

clearChat.addEventListener("click", () => {
  planOutput.textContent = "No plan yet.";
  planOutput.classList.add("empty");
  workerOutput.textContent = "No worker output yet.";
  workerOutput.classList.add("empty");
  finalOutput.textContent = "No response yet.";
  finalOutput.classList.add("empty");
  chatInput.value = "";
  setStatus("Cleared.");
});

const fetchJson = async (url, options) => {
  const response = await fetch(url, options);
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with ${response.status}`);
  }
  return response.json();
};

const loadFiles = async (path = "") => {
  try {
    const query = path ? `?path=${encodeURIComponent(path)}` : "";
    const data = await fetchJson(`/api/files${query}`);
    currentPath = data.path || "";
    pathDisplay.value = currentPath || "/";
    fileList.innerHTML = "";
    if (!data.entries || data.entries.length === 0) {
      const empty = document.createElement("div");
      empty.classList.add("empty");
      empty.textContent = "No files here.";
      fileList.appendChild(empty);
      return;
    }
    data.entries.forEach((entry) => {
      const row = document.createElement("div");
      row.classList.add("file-item");
      const name = document.createElement("span");
      name.textContent = entry.name;
      const type = document.createElement("span");
      type.classList.add("type");
      type.textContent = entry.directory ? "dir" : "file";
      row.appendChild(name);
      row.appendChild(type);
      row.addEventListener("click", () => {
        if (entry.directory) {
          loadFiles(entry.path);
        } else {
          openFile(entry.path);
        }
      });
      fileList.appendChild(row);
    });
  } catch (error) {
    fileList.innerHTML = "";
    const empty = document.createElement("div");
    empty.classList.add("empty");
    empty.textContent = error.message;
    fileList.appendChild(empty);
  }
};

const openFile = async (path) => {
  try {
    const data = await fetchJson(`/api/files/content?path=${encodeURIComponent(path)}`);
    selectedFile = data.path;
    editor.value = data.content || "";
    editorPath.textContent = data.path;
  } catch (error) {
    editorPath.textContent = "Failed to load file";
  }
};

const saveCurrentFile = async () => {
  if (!selectedFile) {
    editorPath.textContent = "Select a file first";
    return;
  }
  try {
    await fetchJson("/api/files/content", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path: selectedFile, content: editor.value }),
    });
    editorPath.textContent = `${selectedFile} (saved)`;
    loadFiles(currentPath);
  } catch (error) {
    editorPath.textContent = "Save failed";
  }
};

const createNewFile = async () => {
  const name = newFileName.value.trim();
  if (!name) {
    return;
  }
  const targetPath = currentPath ? `${currentPath}/${name}` : name;
  try {
    await fetchJson("/api/files/content", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path: targetPath, content: "" }),
    });
    newFileName.value = "";
    loadFiles(currentPath);
    openFile(targetPath);
  } catch (error) {
    editorPath.textContent = "Create failed";
  }
};

upDir.addEventListener("click", () => {
  if (!currentPath) {
    return;
  }
  const segments = currentPath.split("/").filter(Boolean);
  segments.pop();
  loadFiles(segments.join("/"));
});

refreshFiles.addEventListener("click", () => loadFiles(currentPath));
saveFile.addEventListener("click", saveCurrentFile);
createFile.addEventListener("click", createNewFile);

// Skills management functions
const loadSkills = async () => {
  try {
    skillsData = await fetchJson("/api/config/skills");
    renderSkillsForCurrentTab();
    populateWorkerRoles();
  } catch (error) {
    console.error("Failed to load skills:", error);
  }
};

const populateWorkerRoles = () => {
  if (!skillsData || !skillsData.workerRoles) return;
  workerRoleSelect.innerHTML = "";
  skillsData.workerRoles.forEach((role) => {
    const option = document.createElement("option");
    option.value = role;
    option.textContent = role.charAt(0).toUpperCase() + role.slice(1);
    workerRoleSelect.appendChild(option);
  });
  if (skillsData.workerRoles.length > 0) {
    currentWorkerRole = skillsData.workerRoles[0];
    workerRoleSelect.value = currentWorkerRole;
  }
};

const renderSkillsForCurrentTab = () => {
  if (!skillsData) return;
  const activeTab = document.querySelector(".skills-tabs .tab.active")?.dataset.tab;
  if (!activeTab) return;

  switch (activeTab) {
    case "orchestrator":
      renderSkillsList("orchestrator-skills", skillsData.orchestrator, "orchestrator");
      break;
    case "synthesis":
      renderSkillsList("synthesis-skills", skillsData.synthesis, "synthesis");
      break;
    case "worker-defaults":
      renderSkillsList("worker-defaults-skills", skillsData.workerDefaults, "worker-defaults");
      break;
    case "workers":
      const roleSkills = skillsData.workers[currentWorkerRole] || [];
      renderSkillsList("worker-role-skills", roleSkills, `workers/${currentWorkerRole}`);
      break;
  }
};

const renderSkillsList = (containerId, skills, agentType) => {
  const container = document.getElementById(containerId);
  container.innerHTML = "";

  if (!skills || skills.length === 0) {
    const empty = document.createElement("div");
    empty.classList.add("empty-skills");
    empty.textContent = "No skills configured.";
    container.appendChild(empty);
    return;
  }

  skills.forEach((skill, index) => {
    const card = document.createElement("div");
    card.classList.add("skill-card");

    const header = document.createElement("div");
    header.classList.add("skill-header");

    const name = document.createElement("span");
    name.classList.add("skill-name");
    name.textContent = skill.name || "Unnamed Skill";

    const actions = document.createElement("div");
    actions.classList.add("skill-actions");

    const editBtn = document.createElement("button");
    editBtn.textContent = "Edit";
    editBtn.classList.add("ghost");
    editBtn.addEventListener("click", () => openSkillModal(agentType, index, skill));

    const deleteBtn = document.createElement("button");
    deleteBtn.textContent = "Delete";
    deleteBtn.classList.add("ghost");
    deleteBtn.addEventListener("click", () => deleteSkill(agentType, index));

    actions.appendChild(editBtn);
    actions.appendChild(deleteBtn);
    header.appendChild(name);
    header.appendChild(actions);

    const desc = document.createElement("div");
    desc.classList.add("skill-desc");
    desc.textContent = skill.description || "";

    const instructions = document.createElement("div");
    instructions.classList.add("skill-instructions");
    instructions.textContent = skill.instructions || "No instructions";

    card.appendChild(header);
    if (skill.description) card.appendChild(desc);
    card.appendChild(instructions);
    container.appendChild(card);
  });
};

const openSkillModal = (agentType, index, skill = null) => {
  const isNew = skill === null;
  const overlay = document.createElement("div");
  overlay.classList.add("modal-overlay");

  overlay.innerHTML = `
    <div class="modal">
      <h3>${isNew ? "Add" : "Edit"} Skill</h3>
      <div class="form-group">
        <label for="skill-name">Name</label>
        <input type="text" id="skill-name" value="${skill?.name || ""}" placeholder="e.g., Java Expert">
      </div>
      <div class="form-group">
        <label for="skill-desc">Description</label>
        <input type="text" id="skill-desc" value="${skill?.description || ""}" placeholder="Brief description">
      </div>
      <div class="form-group">
        <label for="skill-instructions">Instructions</label>
        <textarea id="skill-instructions" placeholder="Detailed instructions for the agent...">${skill?.instructions || ""}</textarea>
      </div>
      <div class="modal-actions">
        <button class="cancel">Cancel</button>
        <button class="save">Save</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  overlay.querySelector(".cancel").addEventListener("click", () => overlay.remove());
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) overlay.remove();
  });

  overlay.querySelector(".save").addEventListener("click", async () => {
    const newSkill = {
      name: document.getElementById("skill-name").value,
      description: document.getElementById("skill-desc").value,
      instructions: document.getElementById("skill-instructions").value,
    };
    await saveSkill(agentType, index, newSkill, isNew);
    overlay.remove();
  });
};

const saveSkill = async (agentType, index, skill, isNew) => {
  let skills = getSkillsForAgentType(agentType);
  if (isNew) {
    skills.push(skill);
  } else {
    skills[index] = skill;
  }

  try {
    await fetchJson(`/api/config/skills/${agentType}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(skills),
    });
    await loadSkills();
  } catch (error) {
    console.error("Failed to save skill:", error);
  }
};

const deleteSkill = async (agentType, index) => {
  if (!confirm("Are you sure you want to delete this skill?")) return;

  let skills = getSkillsForAgentType(agentType);
  skills.splice(index, 1);

  try {
    await fetchJson(`/api/config/skills/${agentType}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(skills),
    });
    await loadSkills();
  } catch (error) {
    console.error("Failed to delete skill:", error);
  }
};

const getSkillsForAgentType = (agentType) => {
  if (agentType === "orchestrator") return [...skillsData.orchestrator];
  if (agentType === "synthesis") return [...skillsData.synthesis];
  if (agentType === "worker-defaults") return [...skillsData.workerDefaults];
  if (agentType.startsWith("workers/")) {
    const role = agentType.replace("workers/", "");
    return [...(skillsData.workers[role] || [])];
  }
  return [];
};

// Tab switching
skillsTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    skillsTabs.forEach((t) => t.classList.remove("active"));
    skillsPanels.forEach((p) => p.classList.remove("active"));
    tab.classList.add("active");
    document.getElementById(`skills-${tab.dataset.tab}`).classList.add("active");
    renderSkillsForCurrentTab();
  });
});

toolButtons.forEach((button) => {
  button.addEventListener("click", () => {
    openDrawer(button.dataset.side);
  });
});

drawerClose.addEventListener("click", closeDrawer);
drawerOverlay.addEventListener("click", closeDrawer);
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeDrawer();
  }
});

// Worker role selection
workerRoleSelect.addEventListener("change", (e) => {
  currentWorkerRole = e.target.value;
  renderSkillsForCurrentTab();
});

// Add skill buttons
document.getElementById("add-orchestrator-skill").addEventListener("click", () => {
  openSkillModal("orchestrator", -1, null);
});

document.getElementById("add-synthesis-skill").addEventListener("click", () => {
  openSkillModal("synthesis", -1, null);
});

document.getElementById("add-worker-defaults-skill").addEventListener("click", () => {
  openSkillModal("worker-defaults", -1, null);
});

document.getElementById("add-worker-role-skill").addEventListener("click", () => {
  openSkillModal(`workers/${currentWorkerRole}`, -1, null);
});

refreshSkills.addEventListener("click", loadSkills);

window.addEventListener("load", () => {
  loadFiles();
  loadSkills();
  setConnection("Workspace loaded");
  const defaultSide = document.querySelector(".floating-tools .tool-button")?.dataset.side || "skills";
  setActiveSidePanel(defaultSide);
});
