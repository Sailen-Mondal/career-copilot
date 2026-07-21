# Frontend Dashboard

The Career Copilot frontend is a sleek, dark-mode productivity dashboard designed for job search management and automation tracking. Inspired by modern developer interfaces like Linear, Raycast, and Vercel, it features a zero-build architecture that runs natively in any modern web browser.

## Features

- **9 Specialized Views**: Feed, Jobs, Analytics, Pipeline, Automation, Audit, Profile, Policy, and Settings views.
- **Command Palette (`Cmd+K` / `Ctrl+K`)**: Keyboard-driven navigation and quick action launcher.
- **Live API Polling**: Periodic status synchronization with the application core backend.
- **Seamless Mock Fallbacks**: Automatic fallback to local mock data when the backend API is offline.
- **Interactive SVG Workflow Graph**: Visual representation of the job application and audit pipeline.
- **Responsive Layout**: Adaptable sidebar and workspace layout built for fast performance.

## How to Run

### Direct Browser Execution (Zero Build Step)
No Node.js, bundlers, or build tools are required. Open `index.html` directly in any web browser:

- Double-click `index.html` in your file explorer, or
- Drag and drop `index.html` into your browser.

### Using Launch Script
From the project root directory, run:

```powershell
.\launch.ps1
```

This launches the local static HTTP server and opens the dashboard in your default browser.

## Architecture

- **Routing**: Lightweight hash-based client routing (`#feed`, `#jobs`, `#policy`, `#analytics`, etc.).
- **State Management**: Reactive central `appState` object managing view selections, filters, and job queues.
- **Rendering**: Modular vanilla JavaScript DOM rendering without external framework dependencies.

## API Integration

- **Target Backend**: Connects to the Spring Boot REST API at `http://localhost:8080`.
- **Offline Mode**: Automatically falls back to in-memory mock datasets if the backend server is unreachable.
