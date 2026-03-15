## Overview

This `components` directory contains **reusable React components** that make up the MultiAgent UI: chat interface, layout shell, configuration panels, dashboards, and settings views.

## Typical Components

- Conversation and planning:
  - Chat container, input, and message components.
  - Plan preview and orchestration status views.
- Layout and chrome:
  - `Header`, `Sidebar`, and `Layout` components that define the main app shell.
- Configuration & management:
  - Role, skill, and tool policy management components.
  - MCP Server and Tool Settings components.
  - Dashboard-style summary and navigation components.

## Agent Notes

- When adding a new screen or panel, create a dedicated component here and integrate it into `Layout` / `Sidebar` / `App`.
- Keep components focused: UI rendering and local state only; delegate API calls and shared state to context providers when possible.
- For styles, add or update matching `.css` files in `frontend/src/styles` rather than inline large style blocks.

