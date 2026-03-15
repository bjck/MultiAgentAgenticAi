# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Project Overview

Multi-agent AI orchestration system built with Spring Boot 3.5 and Spring AI 1.1. The app decomposes user requests into parallel tasks, assigns them to specialized roles, and synthesizes results. Uses Google Gemini (gemini-2.5-flash) by default. Tooling is DB-controlled and can include local tools plus MCP (Model Context Protocol) servers.

## Repo Layout

- Root is a Maven multi-module build.
- `backend/` is a Maven module that compiles sources from `src/main/java` and resources from `src/main/resources`.
- `frontend/` is a Vite React SPA module bundled into the backend fatjar.
- Frontend-specific notes are in `frontend/AGENTS.md`.

## Build & Run Commands

```powershell
# Build everything (produces fatjar)
mvn clean package

# Run full build with tests
mvn clean install

# Run packaged fatjar (includes frontend)
java -jar backend\target\backend-0.0.1-SNAPSHOT.jar

# Deploy to remote server (via profile)
mvn -pl backend -Pdeploy clean install

# Frontend dev server (hot reload)
cd frontend
npm install
npm run dev
```

## Required Environment Variables

- `GOOGLE_API_KEY` - Google AI API key for Gemini access
- `OPENAI_API_KEY` (optional) - OpenAI API key
- `OPENAI_BASE_URL` (optional) - Override OpenAI base URL
- `WORKSPACE_ROOT` (optional) - Root directory for file operations, defaults to current directory
- `LIQUIBASE_ENABLED` (optional) - Set to `true` to enable database migrations and persistence (default: `false`)
- `SPRING_DATASOURCE_URL` (optional) - JDBC URL for PostgreSQL
- `SPRING_DATASOURCE_USERNAME` (optional)
- `SPRING_DATASOURCE_PASSWORD` (optional)

## Architecture

### Orchestration Flow (OrchestratorService)

1. Planning: Orchestrator generates an `OrchestratorPlan` with up to `maxTasks` TaskSpec entries.
2. Execution: Worker agents execute tasks concurrently (controlled by `workerConcurrency`).
3. Synthesis: A synthesis agent combines outputs when multiple workers were used.

### Collaboration Strategies

Supported strategies:
- SIMPLE_SUMMARY
- PROPOSAL_VOTE
- TWO_ROUND_CONVERGE
- SCORECARD_RANKING

Configured per role in `multiagent.role-execution.*`.

### Implementer Role

Only the `implementer` role is allowed to write files by default. Engineering roles are advisory. Implementer tasks are enforced to call `write_file`.

### Tool Call Auditing

Tool calls are logged per session/task in `tool_call_log`. Orchestrator also logs available tool callbacks and tool call usage for debugging.

### Key Components

- `OrchestratorService` - Core orchestration logic (plan -> execute -> synthesize)
- `MultiAgentProperties` - `multiagent.*` config in application.yml
- `FileService` - Workspace-scoped file operations with path traversal protection
- `OrchestrationPersistenceService` - Session, prompt, plan, task, worker-result, and tool-call logging
- `ChatController` - REST endpoints:
  - `POST /api/chat` - Full orchestration (sync)
  - `POST /api/chat/plan` - Plan only
  - `POST /api/chat/stream` - Streaming run (returns runId)
- `WebSocket` streaming endpoint:
  - `/ws/stream?runId=...` - server push events
- `FileController` (`/api/files`) - File list and content read/write
- `ConfigController` (`/api/config`) - Skills, role execution settings, and tool policies
- `ModelController` (`/api/models`) - List available models (provider optional)
- `McpServerController` (`/api/config/mcp-servers`) - MCP server registry (DB-backed)

## Frontend

- Vite React SPA in `frontend/`
- Entry file: `frontend/index.html`
- Build output: bundled into backend JAR (served from frontend JAR dependency)
- Dev proxy: `/api` and `/ws` to `localhost:8080`
- Sidebar includes File Manager, Skills, Role Settings, Tool Policies, and MCP Servers

## MCP Integration

MCP servers are configured in the database and managed via the UI (`Settings → MCP Servers`).
Supported transports:
- `SSE`
- `STREAMABLE_HTTP`

Tool access is granted per role via DB-backed policies (`Settings → Tool Policies`).

## Database & Persistence

PostgreSQL stores:
- Agent configuration, skills, and tool policies
- Orchestration logs: session, prompts, plans, tasks, worker results
- Tool call logs: `tool_call_log`
- External documentation: `external_document`

Migrations are managed via Liquibase in `src/main/resources/db/changelog`.

### Local PostgreSQL with Docker Compose

```powershell
# Start Postgres in background
docker compose up -d

# Stop and remove
docker compose down
```

## Configuration Defaults

```yaml
multiagent:
  max-tasks: 4
  worker-concurrency: 4
  worker-timeout: 90s
  role-execution-defaults:
    rounds: 1
    agents: 1
    collaboration-strategy: simple-summary
```

## Agent Tools Configuration

Tool policies are stored in the database and edited via the UI. There is no `application.yml`
tool configuration. Use:
- `GET /api/config/tools`
- `PUT /api/config/tools`

Local tools registered in-process:
- `http_fetch`
- `arxiv_api_reader` (stores abstracts only; no PDF downloads)

## Agent Skills Configuration

Skills are stored in config and exposed via `/api/config/skills`.

```yaml
multiagent:
  skills:
    orchestrator: []
    synthesis: []
    worker-defaults: []
    workers:
      engineering:
        - name: "Java Expert"
          description: "Expertise in Java development"
          instructions: "Use modern Java features and patterns"
```
