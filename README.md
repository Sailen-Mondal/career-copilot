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

## MVP Order

1. Confirm the source legality matrix and non-functional requirements.
2. Build the modular monolith with Flyway-managed Postgres.
3. Ingest Greenhouse/Lever public job boards first.
4. Add matching, eligibility filtering, and grounded document generation.
5. Run browser automation in shadow mode before live submission.
6. Enable low-volume auto-submit only behind groundedness, confidence, rate-limit, and circuit-breaker checks.

## Guardrail Principle

The product can be fully automated without being unchecked. The approval gate is replaced by blocking verification, autonomy tiers, caps, circuit breakers, audit logs, and a kill switch.
