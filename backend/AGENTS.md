## Overview

This `backend` module is the **Spring Boot** Maven module for the MultiAgent system. It packages and runs the Java backend and serves the bundled frontend.

## Contents

- `pom.xml`: Maven module descriptor for the backend Spring Boot application.
- `target/`: Build output for this module (including the runnable fat JAR).
- **Sources and resources**:
  - Java sources live in the shared `src/main/java` tree at the repo root (`com.bko.*` packages).
  - Resources (config, Liquibase changelogs, static assets) live in `src/main/resources` at the repo root.

## Build & Run

- **Build backend module only**:
  - `mvn -pl backend clean package`
- **Run packaged backend JAR** (serves the frontend from the JAR dependency):
  - `java -jar backend\target\backend-0.0.1-SNAPSHOT.jar`

## Agent Notes

- Treat this directory as the **module wrapper**; most code changes happen in `src/main/java` and `src/main/resources`.
- When updating dependencies or plugins, change `backend/pom.xml`.
- Do not place Java sources under `backend/src`; they will not be compiled by this setup.

