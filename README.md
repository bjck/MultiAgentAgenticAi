# Multi-Agent Agentic AI Studio

Multi-Agent AI Orchestration system built with **Spring Boot 3.5**, **Spring AI 1.1**, and **Google Gemini**. This repository provides a reference implementation for decomposing complex user requests into parallel tasks, assigning them to specialized worker agents, and synthesizing their outputs into a single coherent response.

![Multi-Agent AI Studio UI](assets/demo.png)

## 🚀 Core Capabilities

### 1. Dynamic Task Planning
The **Orchestrator Agent** analyzes user requests and dynamically breaks them down into multiple independent, parallelizable tasks. Each task is defined with a specific goal, required role, and context, ensuring a structured approach to complex problems.

### 2. Parallel Agent Execution
Tasks are executed concurrently by specialized **Worker Agents**. The system manages a thread pool (configurable via `worker-concurrency`) to maximize throughput and reduce the overall time to completion.

### 3. Specialized Worker Roles & Skills
Agents can be assigned specific roles such as **Analysis**, **Research**, **Engineering**, **Design**, **QA**, and **Writing**. Each role (and the Orchestrator/Synthesis phases) can be enhanced with **specialized skills**—specific instructions and context that guide the agent's behavior and output quality.

### 4. Seamless Synthesis
Once all parallel tasks are completed, a **Synthesis Agent** gathers the results, resolves any conflicts, and combines the individual outputs into a unified, high-quality final response.

### 5. Tool Access via MCP and Local Tools
Tooling is controlled by the database, not `application.yml`. The system supports:
- **Local tools** (in-process) such as `http_fetch` and `arxiv_api_reader`.
- **MCP servers** configured via the UI (HTTP/SSE or Streamable HTTP transports).
Roles are granted tools via DB-backed policies, so tools can be enabled/disabled without redeploys.

### 6. Interactive Studio UI
The project includes a modern, dark-themed web interface for:
- **Orchestration**: Direct interaction with the Orchestrator.
- **Real-time Monitoring**: View the generated plan and watch worker outputs as they arrive.
- **Skill Management**: Dynamically add, remove, and configure agent skills for different phases and roles.
- **Workspace Explorer**: Integrated file explorer and code editor for immediate inspection and modification of the workspace.

## 🛠️ Architecture

The system follows a three-phase workflow:

1.  **Plan**: The Orchestrator agent creates an `OrchestratorPlan` containing up to `maxTasks` (default: 4) independent `TaskSpec` entries.
2.  **Execute**: Worker agents execute tasks in parallel. Each worker is assigned a role and provided with the relevant context and skills.
3.  **Synthesize**: If multiple tasks were executed, the synthesis phase combines the outputs. For single-task requests, the worker output is returned directly.

## 💻 Technology Stack

- **Java**: 21
- **Framework**: Spring Boot 3.5.10
- **AI Integration**: Spring AI 1.1.2 (BOM)
- **LLM**: Google Gemini (`gemini-2.5-flash`)
- **Protocols**: Model Context Protocol (MCP) for tool access.
- **Frontend**: Vanilla JS, CSS Custom Properties (Dark/Light mode support).

## ⚙️ Getting Started

### Prerequisites
- Java 21+
- Maven
- `GOOGLE_API_KEY` environment variable.

### Configuration
Key settings in `application.yml`:
```yaml
multiagent:
  max-tasks: 4           # Maximum parallel tasks per request
  worker-concurrency: 4  # Concurrent execution thread pool size
  worker-timeout: 90s    # Per-worker execution timeout
```
Tool configuration is not in `application.yml`. Use the **Settings** panel in the UI to manage:
- Tool policies per role
- MCP server connections (HTTP/SSE or Streamable HTTP)

### Running the Application
```powershell
# Set your API key
$env:GOOGLE_API_KEY = "your-api-key"

# Build and run
mvn spring-boot:run
```
Access the UI at `http://localhost:8080`.

## 📂 Project Structure
- `src/main/java/com/bko/orchestration`: Core orchestration logic and services.
- `src/main/java/com/bko/config`: Spring configuration and multi-agent properties.
- `src/main/java/com/bko/api`: REST controllers for chat, planning, and file operations.
- `src/main/resources/static`: Studio UI assets.
- `assets/`: Documentation assets (images, etc.).
- `deploy/`: Optional deployment examples (Loki/Grafana and Promtail config).

## 📈 Structured Logging and Ingestion

The backend now emits JSON logs by default (`src/main/resources/logback-spring.xml`) and injects
correlation fields via `RequestCorrelationFilter`:

- `requestId`
- `traceId`
- `httpMethod`
- `httpPath`
- optional `runId` and `sessionId` (when available as request params)

This is intended for log ingestion into systems like Loki, ELK/OpenSearch, or Datadog.

### Quick self-hosted setup (Ubuntu)

1. Start Loki + Grafana:

```bash
cd deploy
docker compose -f loki-docker-compose.yml up -d
```

> Security: this compose binds Grafana/Loki to `127.0.0.1` only, so they are not exposed on public interfaces.

2. Install Promtail on the host and use `deploy/promtail-config.yml`:

```bash
# Example install location
sudo mkdir -p /etc/promtail
sudo cp deploy/promtail-config.yml /etc/promtail/config.yml
```

3. Ensure your app runs as systemd unit `ai-agent.service` (or update the regex in the Promtail config).

4. Expose Grafana to your **tailnet only** with Tailscale Serve (no public internet):

```bash
sudo tailscale serve --https=443 http://127.0.0.1:3000
```

Then open `https://<your-hostname>.<tailnet>.ts.net` from a logged-in Tailscale device.

5. In Grafana, add Loki datasource `http://localhost:3100`, and query:

```text
{app="multiagent", unit="ai-agent.service"}
```

Note: `http://<host>:3100` returning `404 page not found` at `/` is expected for Loki.  
Use the datasource URL `http://localhost:3100` inside Grafana or Loki API paths (for example `/loki/api/v1/query`).

### Important note

For token counts to persist in `prompt_log`, deploy with Liquibase migrations enabled:

```bash
export LIQUIBASE_ENABLED=true
```

## 🔌 MCP Servers and Tools

### MCP Server Registry (DB-backed)
MCP connections are stored in the database and managed via the UI:
- `GET /api/config/mcp-servers`
- `PUT /api/config/mcp-servers`

Supported transports:
- `SSE` (Server-Sent Events)
- `STREAMABLE_HTTP`

### Local Tools
Tools registered in-process:
- `http_fetch`: HTTP(S) fetch with size and timeout limits.
- `arxiv_api_reader`: Queries the arXiv API and stores **abstracts only** (no PDF downloads).

### External Documentation Storage
Parsed documents are stored in the `external_document` table with source, title, abstract, authors, categories, and timestamps.

---
*Developed as a reference implementation for Multi-Agent Agentic AI patterns.*



## Local PostgreSQL with Docker Compose

You can spin up a local Postgres database for the app using Docker Compose.

Commands (PowerShell):

```powershell
# Start Postgres in background
docker compose up -d

# View logs
docker compose logs -f postgres

# Stop and remove
docker compose down
```

Default credentials provisioned by docker-compose.yml:
- DB: multiagent
- User: multiagent
- Password: multiagent
- Port: 5432

Configure the application to use this database (example env vars):

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/multiagent"
$env:SPRING_DATASOURCE_USERNAME = "multiagent"
$env:SPRING_DATASOURCE_PASSWORD = "multiagent"
$env:LIQUIBASE_ENABLED = "true"   # apply migrations on startup

mvn spring-boot:run
```

Notes:
- Liquibase changelog is applied when `LIQUIBASE_ENABLED=true`.
- The changelog enables `pgcrypto` (required for `gen_random_uuid()`), which is supported by the official Postgres image.
- Persisted data is stored in a named Docker volume `postgres_data`.
