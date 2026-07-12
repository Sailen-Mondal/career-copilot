# AI Career Copilot

Starter workspace for the revised AI Career Copilot architecture.

This repo is intentionally a walking skeleton, not the full product. It encodes the safest MVP from the plan:

- One modular backend boundary for profile, discovery, matching, generation, automation, applications, notification, and shared code.
- A separately deployable `automation/` folder for browser automation work.
- Docs for architecture, data model, source legality, risk, and delivery milestones.
- Early implementation of the two highest-risk rules: generated claims must be grounded in verified profile facts, and auto-submission is allowed only when the autonomy policy passes.

## Current Shape

```text
career-copilot/
├── app/                        # modular monolith starter
├── automation/                 # browser automation service stub
├── frontend/                   # dashboard stub
├── docs/                       # architecture and delivery docs
└── infra/                      # local infra and CI notes
```

## Current Status

The MVP is **fully implemented and tested**! All modules are integrated and run in local dev mode.

### Implemented Features:
1. **Enhanced Ingestion**: Automatically extracts skills, seniority, and work authorization requirements from public job boards.
2. **Hard Pre-Filters**: Implemented filters for matching work authorization, visa sponsorship, candidate experience years (seniority bounds), and location type/preferences in `MatchingService`.
3. **Transaction Isolation**: Restructured `ApplicationWorkflowService` to run external Vertex AI LLM calls outside database transactions, preventing Hikari pool starvation.
4. **Resilient Circuit Breaker**: Integrates Playwright worker success/failure streams with the database-backed `CircuitBreakerState`, transitioning to `OPEN` on 3 consecutive failures.
5. **Accurate Timestamps**: Added a `created_at` timestamp to the database schema (`V5`) and mapped it to the DTO and dashboard feeds.

---

## Getting Started: 1-Click Launch

We have included a 1-click launcher in the root folder of the project.

To run the entire system (Docker containers, Spring Boot backend, Playwright worker, and opening the browser dashboard):
- **On Windows**: Double-click **`launch.bat`** (or pin it to your taskbar for a true 1-click experience).

The script will automatically check if Docker is running, spin up PostgreSQL and Redis, start the backend server, boot the Playwright worker, and open the browser dashboard once the backend is healthy.

---

## Guardrail Principle

The product can be fully automated without being unchecked. The approval gate is replaced by blocking verification, autonomy tiers, caps, circuit breakers, audit logs, and a kill switch.
