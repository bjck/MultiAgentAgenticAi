## Overview

This `styles` directory contains **CSS files** for the MultiAgent frontend. Each major component or area of the UI has a corresponding stylesheet, plus a few global layout and base-style files.

## Conventions

- One CSS file per major component (e.g., `Header.css`, `Sidebar.css`, `PlanPreview.css`), imported by that component.
- Shared layout and global rules live in files like `App.css` and `index.css`.
- Class names are usually scoped to a component (e.g., `.sidebar-container`, `.plan-preview-panel`) to minimize cross‑component coupling.

## Agent Notes

- When introducing a new component, add a matching CSS file here and import it from the component.
- Prefer using existing variables, spacing, and typography patterns to keep the UI consistent.
- Avoid adding component‑specific rules to global files unless the style is truly shared across multiple areas.

