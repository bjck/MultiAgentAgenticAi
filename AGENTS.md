# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

Multi-agent AI orchestration system built with Spring Boot 3.5 and Spring AI 1.1. The application decomposes user requests into parallel tasks, assigns them to specialized worker agents, and synthesizes results into a unified response. Uses Google Gemini (gemini-2.5-flash) as the LLM backend and MCP (Model Context Protocol) for filesystem tool access.

## Build & Run Commands

```powershell
# Build (skip tests)
mvn clean package -DskipTests

# Run tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run application (requires GOOGLE_API_KEY env var)
mvn spring-boot:run

# Run the packaged JAR
java -jar target\MultiAgent-0.0.1-SNAPSHOT.jar
```

## Required Environment Variables

- `GOOGLE_API_KEY` - Google AI API key for Gemini access
- `WORKSPACE_ROOT` (optional) - Root directory for file operations, defaults to current directory
- `MCP_FS_*` (optional) - Override MCP filesystem server command/args for non-Windows platforms

## Architecture

### Orchestration Flow (OrchestratorService)

1. **Planning**: Orchestrator agent analyzes user request and creates an `OrchestratorPlan` with up to `maxTasks` independent `TaskSpec` entries
2. **Parallel Execution**: Worker agents execute tasks concurrently (controlled by `workerConcurrency`), each assigned a role from the configured `workerRoles`
3. **Synthesis**: If multiple workers were used, a synthesis agent combines outputs into a coherent response

### Key Components

- `OrchestratorService` - Core orchestration logic with three-phase workflow (plan → execute → synthesize)
- `MultiAgentProperties` - Configuration via `multiagent.*` prefix in application.yml
- `FileService` - Workspace-scoped file operations with path traversal protection
- `ChatController` - REST endpoints:
  - `POST /api/chat` - Full orchestration (plan + execute + synthesize)
  - `POST /api/chat/plan` - Preview plan only without execution
- `FileController` (`/api/files`) - File listing/read/write endpoints

### Worker Roles

Configurable via `multiagent.worker-roles`: research, design, engineering, qa, writing, general

### MCP Integration

The application integrates with MCP filesystem server to give agents file access within the workspace. On Windows, it uses `cmd.exe /c npx -y @modelcontextprotocol/server-filesystem`. Override via `MCP_FS_*` env vars for other platforms.

## Configuration Defaults

```yaml
multiagent:
  max-tasks: 4           # Maximum parallel tasks per request
  worker-concurrency: 4  # Thread pool size for workers
  worker-timeout: 90s    # Per-worker timeout
```

## Agent Skills Configuration

Skills can be assigned to agents to provide specialized instructions and capabilities. Each skill has a name, description, and instructions that are included in the agent's system prompt.

### Configuration Structure

```yaml
multiagent:
  skills:
    # Skills for the orchestrator agent (planning phase)
    orchestrator:
      - name: "Domain Expert"
        description: "Expertise in specific domain"
        instructions: "Apply domain knowledge when planning tasks"
    
    # Skills for the synthesis agent (combining worker outputs)
    synthesis:
      - name: "Report Writer"
        description: "Expert at writing reports"
        instructions: "Format output as a structured report"
    
    # Default skills applied to all worker agents
    worker-defaults:
      - name: "Best Practices"
        description: "Follow coding best practices"
        instructions: "Always follow SOLID principles"
    
    # Role-specific skills for worker agents
    workers:
      engineering:
        - name: "Java Expert"
          description: "Expertise in Java development"
          instructions: "Use modern Java features and patterns"
      research:
        - name: "Academic Research"
          description: "Academic research methodology"
          instructions: "Cite sources and use proper methodology"
```

### Skill Properties

- `name` - Short identifier for the skill
- `description` - Brief explanation of what the skill provides
- `instructions` - Detailed instructions to guide the agent's behavior
