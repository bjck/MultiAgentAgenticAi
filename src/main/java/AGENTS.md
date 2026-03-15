## Overview

This `src/main/java` directory contains the **Java source code** for the MultiAgent Spring Boot backend.

## Structure

- `com/bko/`: Root package for all backend code.
  - `api/`: REST controllers (e.g., chat, config, file, MCP servers).
  - `config/`: Spring configuration and property binding (`MultiAgentProperties`, tool callbacks, etc.).
  - `entity/`: JPA entities (sessions, tools, external documents, MCP servers, etc.).
  - `files/`: File service and related helpers.
  - `mcp/`: MCP client integration and supporting classes.
  - `orchestration`: Orchestrator core (planning, execution, synthesis, skills, task management).
  - `repository/`: Spring Data repositories for entities.
  - `stream/`: WebSocket and streaming support.
  - `tools/`: In‑process tool implementations exposed to agents.
  - `MultiAgentApplication.java`: Spring Boot application entry point.

## Agent Notes

- Follow existing package boundaries when adding new features (e.g., orchestration logic under `orchestration`, new API endpoints under `api`).
- Use Spring Data repositories and JPA entities for persistence; remember to update Liquibase changelogs when altering database schemas.
- Keep orchestration flow changes localized to `orchestration` packages to avoid coupling transport (REST/WebSocket) with core logic.

