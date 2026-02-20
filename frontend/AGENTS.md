# Frontend/src Directory Documentation
This document provides an overview of the files and directories within frontend/src, detailing their general purpose and logic. It is designed to serve as a comprehensive reference for future agents.

Directory Structure and File Descriptions

## frontend/src/
``App.jsx``: The main application component, responsible for orchestrating the overall layout, routing, and top-level state management of the application.
`index.jsx`: The entry point of the React application. It renders the App component into the DOM, typically setting up global providers like ThemeProvider and WebSocketProvider.

## frontend/src/components/
This directory contains reusable UI components that make up various parts of the application's interface.

`ChatContainer.jsx`: Manages and displays the main chat interface, including the history of messages and the input area.
`ChatInput.jsx`: Handles user input for sending messages within the chat interface.
`ChatMessage.jsx`: Renders an individual chat message, displaying content, sender, and other relevant information.
`FileBrowser.jsx`: Provides an interactive interface for browsing and navigating the file system within the application's context.
`FileEditor.jsx`: A component designed for viewing and editing the content of files.
`Header.jsx`: The application's header component, typically containing the title, navigation links, and global actions.
`Layout.jsx`: Defines the overall structural layout of the application, often wrapping other components to provide a consistent visual structure.
`ModelSelector.jsx`: Allows users or agents to select and configure different AI models for various tasks.
`PlanPreview.jsx`: Displays a visual or textual preview of an agent's planned actions or workflow.
`RoleSettings.jsx`: Manages and displays settings and configurations related to agent roles and permissions.
`Sidebar.jsx`: A collapsible or fixed side panel, often used for navigation, supplementary information, or tool access.
`SkillManager.jsx`: Provides an interface for managing, enabling, or disabling various skills and capabilities of an agent.

## frontend/src/context/
This directory contains React Context providers, which are used to share state and functions across the component tree without prop drilling.

`ThemeProvider.jsx`: A React Context provider that supplies theme-related data (e.g., color schemes, typography) to all consumer components.
`WebSocketProvider.jsx`: A React Context provider that manages WebSocket connections and provides real-time data or communication capabilities to its consumer components.

## frontend/src/styles/
This directory holds CSS files that define the visual styling for various components and the overall application.

- `App.css`: Contains global styles or styles specifically for the App component's layout and structure.
- `ChatContainer.css`: Styles specific to the ChatContainer component.
- `ChatInput.css`: Styles specific to the ChatInput component.
- `ChatMessage.css`: Styles specific to the ChatMessage component.
- `FileBrowser.css`: Styles specific to the FileBrowser component.
- `FileEditor.css`: Styles specific to the FileEditor component.
- `index.css`: Often contains global CSS resets, base styles, or utility classes applied across the entire application.
- `ModelSelector.css`: Styles specific to the ModelSelector component.
- `PlanPreview.css`: Styles specific to the PlanPreview component.
- `RoleSettings.css`: Styles specific to the RoleSettings component.
- `Sidebar.css`: Styles specific to the Sidebar component.
- `SkillManager.css`: Styles specific to the SkillManager component.