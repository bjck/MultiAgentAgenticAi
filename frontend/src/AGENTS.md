## Overview

This `frontend/src` directory contains the **React application source** for the MultiAgent UI: entry points, layout components, context providers, and styles.

## Key Files & Folders

- `App.jsx`: Root React component that composes the main layout, sidebar, chat area, and plan preview.
- `index.jsx`: React entry file that mounts `App` and wires up global providers.
- `components/`: All major UI components (chat, layout, header, sidebar, dashboards, configuration panels).
- `context/`: React Context providers (e.g., WebSocket, theme, app‑level state).
- `styles/`: CSS modules for global app layout and individual components.

## Agent Notes

- Prefer adding new UI elements under `components/` and co-locating their styles in `styles/`.
- When wiring new global state or real-time behavior, add or extend a provider under `context/` and wrap `App` in `index.jsx`.
- Keep top‑level logic (routing, global providers) in `App.jsx` and `index.jsx`; avoid overloading leaf components with cross‑cutting concerns.

