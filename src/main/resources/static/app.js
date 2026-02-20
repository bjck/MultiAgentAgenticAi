const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");
const chatStatus = document.getElementById("chat-status");
const activeAgentsLabel = document.getElementById("active-agents");
const planOutput = document.getElementById("plan-output");
const workerOutput = document.getElementById("worker-output");
const finalOutput = document.getElementById("final-output");
const clearChat = document.getElementById("clear-chat");
const connectionLabel = document.getElementById("connection-label");
const previewPlanBtn = document.getElementById("preview-plan");
const providerSelect = document.getElementById("provider-select");
const modelSelect = document.getElementById("model-select");
const modelSelectGroup = document.getElementById("model-select-group");

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
const roleSettingsDefaultsEl = document.getElementById("role-settings-defaults");
const roleSettingsListEl = document.getElementById("role-settings-list");
const saveRoleSettingsBtn = document.getElementById("save-role-settings");

let currentPath = "";
let selectedFile = "";
let skillsData = null;
let currentWorkerRole = "";
let currentPlan = null;
let currentMessage = "";
let streamSocket = null;
let currentRunId = "";
let lastEventId = 0;
let reconnectAttempts = 0;
let reconnectTimer = null;
let streamClosedByClient = false;
const workerBuffers = new Map();
const workerCards = new Map();
const activeAgents = new Map();
let roleSettings = null;

const setStatus = (message) => {
  chatStatus.textContent = message;
};

const setConnection = (message) => {
  connectionLabel.textContent = message;
};

const resetStreamState = () => {
  workerBuffers.clear();
  workerCards.clear();
  activeAgents.clear();
  lastEventId = 0;
  currentPlan = null;
  if (activeAgentsLabel) {
    activeAgentsLabel.textContent = "None";
  }
};

const updateActiveAgents = () => {
  if (!activeAgentsLabel) return;
  if (activeAgents.size === 0) {
    activeAgentsLabel.textContent = "None";
    return;
  }
  const entries = Array.from(activeAgents.entries()).map(([taskId, role]) => `${role} (${taskId})`);
  activeAgentsLabel.textContent = entries.join(", ");
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

    // NEW: Agent Visual Feedback
    const agentFeedback = document.createElement("div");
    agentFeedback.classList.add("agent-feedback");
    agentFeedback.textContent = `Agent: ${result.role}`; // Display agent role

    heading.appendChild(role);
    heading.appendChild(taskId);
    heading.appendChild(agentFeedback); // Add agent feedback to the heading
    const body = document.createElement("div");
    body.classList.add("markdown-content");
    body.innerHTML = renderMarkdown(result.output || "");
    card.appendChild(heading);
    card.appendChild(body);
    workerOutput.appendChild(card);
  });
};

const ensureWorkerCard = (taskId, role) => {
  if (workerCards.has(taskId)) {
    return workerCards.get(taskId);
  }
  workerOutput.classList.remove("empty");
  if (workerCards.size === 0) {
    workerOutput.innerHTML = "";
  }
  const card = document.createElement("div");
  card.classList.add("worker-card");
  card.dataset.taskId = taskId;

  const heading = document.createElement("div");
  heading.classList.add("worker-heading");

  const roleEl = document.createElement("span");
  roleEl.classList.add("role");
  roleEl.textContent = role || "worker";

  const taskEl = document.createElement("span");
  taskEl.classList.add("task-id");
  taskEl.textContent = taskId || "";

  const statusEl = document.createElement("span");
  statusEl.classList.add("worker-status");
  statusEl.textContent = "Queued";

  heading.appendChild(roleEl);
  heading.appendChild(taskEl);
  heading.appendChild(statusEl);

  const body = document.createElement("div");
  body.classList.add("markdown-content");
  body.textContent = "";

  card.appendChild(heading);
  card.appendChild(body);
  workerOutput.appendChild(card);
  workerCards.set(taskId, { card, body, statusEl });
  return workerCards.get(taskId);
};

const appendWorkerOutput = (taskId, role, chunk) => {
  const entry = ensureWorkerCard(taskId, role);
  const prev = workerBuffers.get(taskId) || "";
  const next = prev + (chunk || "");
  workerBuffers.set(taskId, next);
  entry.body.innerHTML = renderMarkdown(next);
};

const setWorkerStatus = (taskId, role, statusText, isActive) => {
  const entry = ensureWorkerCard(taskId, role);
  entry.statusEl.textContent = statusText;
  entry.card.classList.toggle("active", Boolean(isActive));
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

const closeStreamSocket = () => {
  streamClosedByClient = true;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (streamSocket) {
    streamSocket.close();
    streamSocket = null;
  }
};

const scheduleReconnect = () => {
  if (streamClosedByClient) return;
  reconnectAttempts += 1;
  const delay = Math.min(8000, 500 * Math.pow(1.6, reconnectAttempts));
  reconnectTimer = setTimeout(() => {
    connectStream(currentRunId);
  }, delay);
  setConnection(`Reconnecting in ${Math.round(delay / 1000)}s...`);
};

const connectStream = (runId) => {
  if (!runId) return;
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  const url = `${protocol}://${window.location.host}/ws/stream?runId=${encodeURIComponent(runId)}&since=${encodeURIComponent(lastEventId)}`;
  streamClosedByClient = false;
  streamSocket = new WebSocket(url);

  streamSocket.onopen = () => {
    reconnectAttempts = 0;
    setConnection("Live stream connected");
  };

  streamSocket.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data);
      if (payload && typeof payload.id === "number") {
        lastEventId = Math.max(lastEventId, payload.id);
      }
      handleStreamEvent(payload);
    } catch (err) {
      console.error("Stream parse error:", err);
    }
  };

  streamSocket.onclose = () => {
    if (!streamClosedByClient) {
      scheduleReconnect();
    }
  };

  streamSocket.onerror = () => {
    streamSocket.close();
  };
};

const handleStreamEvent = (event) => {
  if (!event || !event.type) return;
  const data = event.data || {};
  switch (event.type) {
    case "status":
      setStatus(data.message || "Working...");
      break;
    case "plan":
      currentPlan = data;
      renderPlan(data);
      break;
    case "plan-update":
      currentPlan = {
        objective: data.objective || currentPlan?.objective || "",
        tasks: [...(currentPlan?.tasks || []), ...(data.tasks || [])],
      };
      renderPlan(currentPlan);
      break;
    case "task-start":
      activeAgents.set(data.taskId, data.role || "worker");
      updateActiveAgents();
      setWorkerStatus(data.taskId, data.role, "Running", true);
      break;
    case "task-output":
      appendWorkerOutput(data.taskId, data.role, data.chunk || "");
      if (data.done) {
        setWorkerStatus(data.taskId, data.role, "Wrapping up", true);
      }
      break;
    case "task-complete":
      activeAgents.delete(data.taskId);
      updateActiveAgents();
      setWorkerStatus(data.taskId, data.role, "Complete", false);
      break;
    case "final":
      renderFinal(data.finalAnswer || "");
      break;
    case "error":
      setStatus("Failed to run agents.");
      finalOutput.classList.remove("markdown-content");
      finalOutput.textContent = data.message || "Streaming error.";
      finalOutput.classList.remove("empty");
      break;
    case "run-complete":
      setStatus("Agents complete.");
      closeStreamSocket();
      break;
  }
};

const sendChat = async (message) => {
  setStatus("Starting stream...");
  const provider = providerSelect.value;
  const model = modelSelect.value;
  closeStreamSocket();
  resetStreamState();
  planOutput.textContent = "Waiting for plan...";
  planOutput.classList.remove("empty");
  workerOutput.textContent = "Awaiting worker output...";
  workerOutput.classList.remove("empty");
  finalOutput.textContent = "Streaming response...";
  finalOutput.classList.remove("empty");
  finalOutput.classList.remove("markdown-content");
  try {
    const response = await fetch("/api/chat/stream", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, provider, model }),
    });
    if (!response.ok) {
      throw new Error(`Chat failed with ${response.status}`);
    }
    const data = await response.json();
    currentRunId = data.runId;
    lastEventId = 0;
    connectStream(currentRunId);
    setConnection("Stream starting...");
  } catch (error) {
    setStatus("Failed to start stream.");
    finalOutput.classList.remove("markdown-content");
    finalOutput.textContent = error.message;
    finalOutput.classList.remove("empty");
  }
};

// Plan preview functionality
const previewPlan = async (message) => {
  setStatus("Generating plan preview...");
  const provider = providerSelect.value;
  const model = modelSelect.value;
  try {
    const response = await fetch("/api/chat/plan", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, provider, model }),
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
  closeStreamSocket();
  currentRunId = "";
  resetStreamState();
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

const loadRoleSettings = async () => {
  try {
    roleSettings = await fetchJson("/api/config/role-settings");
    renderRoleSettings();
  } catch (error) {
    console.error("Failed to load role settings:", error);
  }
};

const renderRoleSettings = () => {
  if (!roleSettingsDefaultsEl || !roleSettingsListEl || !roleSettings) return;
  roleSettingsDefaultsEl.innerHTML = "";
  roleSettingsListEl.innerHTML = "";

  const defaultsCard = document.createElement("div");
  defaultsCard.classList.add("role-setting-card");
  defaultsCard.innerHTML = `
    <h4>Defaults</h4>
    <div class="role-setting-grid">
      <div>
        <label>Rounds</label>
        <input type="number" min="1" step="1" value="${roleSettings.defaults?.rounds || 1}" data-role="__defaults__" data-field="rounds">
      </div>
      <div>
        <label>Agents</label>
        <input type="number" min="1" step="1" value="${roleSettings.defaults?.agents || 1}" data-role="__defaults__" data-field="agents">
      </div>
    </div>
  `;
  roleSettingsDefaultsEl.appendChild(defaultsCard);

  (roleSettings.workerRoles || []).forEach((role) => {
    const cfg = roleSettings.roles?.[role] || roleSettings.defaults || { rounds: 1, agents: 1 };
    const card = document.createElement("div");
    card.classList.add("role-setting-card");
    card.innerHTML = `
      <h4>${role}</h4>
      <div class="role-setting-grid">
        <div>
          <label>Rounds</label>
          <input type="number" min="1" step="1" value="${cfg.rounds || 1}" data-role="${role}" data-field="rounds">
        </div>
        <div>
          <label>Agents</label>
          <input type="number" min="1" step="1" value="${cfg.agents || 1}" data-role="${role}" data-field="agents">
        </div>
      </div>
    `;
    roleSettingsListEl.appendChild(card);
  });
};

const saveRoleSettings = async () => {
  if (!roleSettingsDefaultsEl || !roleSettingsListEl) return;
  const inputs = document.querySelectorAll("#skills-role-settings input[data-role]");
  const defaults = { rounds: 1, agents: 1 };
  const roles = {};
  inputs.forEach((input) => {
    const role = input.dataset.role;
    const field = input.dataset.field;
    const value = Math.max(1, parseInt(input.value || "1", 10));
    if (role === "__defaults__") {
      defaults[field] = value;
    } else {
      roles[role] = roles[role] || {};
      roles[role][field] = value;
    }
  });
  try {
    roleSettings = await fetchJson("/api/config/role-settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ defaults, roles }),
    });
    renderRoleSettings();
  } catch (error) {
    console.error("Failed to save role settings:", error);
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
    case "role-settings":
      renderRoleSettings();
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

if (saveRoleSettingsBtn) {
  saveRoleSettingsBtn.addEventListener("click", saveRoleSettings);
}

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

refreshSkills.addEventListener("click", () => {
  loadSkills();
  loadRoleSettings();
});

const loadModels = async (provider) => {
  try {
    const prov = provider || providerSelect.value;
    const data = await fetchJson(`/api/models?provider=${encodeURIComponent(prov)}`);
    modelSelect.innerHTML = "";
    if (data && data.data) {
      data.data.forEach((m) => {
        const opt = document.createElement("option");
        opt.value = m.id;
        opt.textContent = m.id;
        modelSelect.appendChild(opt);
      });
    }
  } catch (err) {
    console.error("Failed to load models:", err);
  }
};

providerSelect.addEventListener("change", () => {
  // Show model selector for providers with listable models (OPENAI and GOOGLE)
  if (providerSelect.value === "OPENAI" || providerSelect.value === "GOOGLE") {
    modelSelectGroup.style.display = "flex";
    loadModels(providerSelect.value);
  } else {
    modelSelectGroup.style.display = "none";
  }
});

window.addEventListener("load", () => {
  loadFiles();
  loadSkills();
  loadRoleSettings();
  if (providerSelect.value === "OPENAI" || providerSelect.value === "GOOGLE") {
    modelSelectGroup.style.display = "flex";
    loadModels(providerSelect.value);
  }
  setConnection("Workspace loaded");
  const defaultSide = document.querySelector(".floating-tools .tool-button")?.dataset.side || "skills";
  setActiveSidePanel(defaultSide);
});
