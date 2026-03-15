## Overview

The root `src` directory hosts the **backend application code** and tests for the MultiAgent Spring Boot service.

## Structure

- `main/`: Production code and resources.
  - `main/java/`: Java packages under `com.bko.*` (REST APIs, orchestration services, repositories, tools, etc.).
  - `main/resources/`: Spring configuration, Liquibase changelogs, and static assets.
- `test/`: Test sources (unit and integration tests) mirroring the `main` package structure.

## Agent Notes

- Application entry point: `com.bko.MultiAgentApplication` in `main/java`.
- When adding new backend features, place Java classes under the appropriate `com.bko` subpackage and update Liquibase changelogs in `main/resources/db/changelog` if you change the database schema.

