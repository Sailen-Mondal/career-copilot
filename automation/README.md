# Automation Worker

The Automation Worker is an isolated TypeScript and Playwright browser automation service designed to process job application form-filling tasks asynchronously via Redis Streams.

## Architecture

The worker runs independently from the backend Spring Boot core:

1. **Queue Listening**: Listens to application tasks on Redis Stream `cc:automation:jobs`.
2. **Form Execution**: Spawns headless Playwright Chromium instances to navigate apply URLs and populate form fields in shadow mode.
3. **Artifact Generation**: Captures screenshots of filled forms and produces execution audit logs.
4. **Result Dispatch**: Publishes completion statuses and artifact paths back to Redis Stream `cc:automation:results`.

## Key Components

- **`redis-consumer`**: Manages Redis Stream subscription, consumer group offsets, and task polling loops.
- **`shadow-worker`**: Orchestrates browser page lifecycle, form input injection, and field validation.
- **`platform-detector`**: Identifies ATS platforms (e.g., Greenhouse, Lever, Workday) and selects selector strategies.
- **`resume-writer`**: Formats and generates custom PDF resumes and cover letters prior to upload.

## Safety & Compliance

- **Shadow Mode Guard**: In shadow mode, the worker populates fields and verifies DOM structure but **never clicks final form submit buttons**.
- **Comprehensive Logging**: Every action, field mapping result, and unhandled custom question is recorded in structured logs.
- **Kill Switch Compliance**: Respects emergency pause signals dispatched via Redis commands.

## Setup & Running

### Local Environment

```bash
# Install dependencies
npm install

# Install Playwright browser binaries
npx playwright install chromium

# Start the worker process
npm start
```

### Docker Container

Build and containerize using the included Dockerfile:

```bash
docker build -t career-copilot-automation-worker .
docker run -e REDIS_HOST=localhost -e REDIS_PORT=6379 career-copilot-automation-worker
```
