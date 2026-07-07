# App

The app folder is the modular monolith starter.

The current Java classes are dependency-free so the guardrail logic can be compiled and smoke-tested before the full Spring Boot build is wired up.

Planned package responsibilities:

- `profile`: verified user facts and policy preferences
- `discovery`: normalized jobs and source ingestion
- `matching`: eligibility and scoring
- `generation`: generated document metadata and groundedness verification
- `automation`: autonomy policy, circuit breaker state, browser-command boundaries
- `applications`: application lifecycle and audit trail
- `notification`: alerting boundaries
- `shared`: cross-package event contracts
