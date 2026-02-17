const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");
const chatStatus = document.getElementById("chat-status");
const planOutput = document.getElementById("plan-output");
const workerOutput = document.getElementById("worker-output");
const finalOutput = document.getElementById("final-output");
const clearChat = document.getElementById("clear-chat");
const connectionLabel = document.getElementById("connection-label");

const fileList = document.getElementById("file-list");
const pathDisplay = document.getElementById("path-display");
const upDir = document.getElementById("up-dir");
const refreshFiles = document.getElementById("refresh-files");
const newFileName = document.getElementById("new-file-name");
const createFile = document.getElementById("create-file");
const editor = document.getElementById("editor");
const editorPath = document.getElementById("editor-path");
const saveFile = document.getElementById("save-file");

let currentPath = "";
let selectedFile = "";

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
    card.classList.add("task");
    const heading = document.createElement("div");
    const role = document.createElement("span");
    role.classList.add("role");
    role.textContent = result.role || "worker";
    const taskId = document.createElement("span");
    taskId.textContent = result.taskId || "";
    heading.appendChild(role);
    heading.appendChild(taskId);
    const body = document.createElement("div");
    body.textContent = result.output || "";
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
  finalOutput.textContent = finalAnswer;
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
    finalOutput.textContent = error.message;
    finalOutput.classList.remove("empty");
  }
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

window.addEventListener("load", () => {
  loadFiles();
  setConnection("Workspace loaded");
});
