# Software Requirements

## Goal

Build a personal AI Career Copilot that discovers suitable jobs, scores fit, generates grounded application materials, and submits applications automatically only when automated guardrails pass.

## MVP Scope

- Greenhouse public job ingestion
- Deduped job store
- Eligibility filter
- Match scoring
- Resume and cover-letter generation from verified profile facts
- Blocking groundedness verification
- Shadow-mode browser automation
- Application audit log
- Dashboard feed, autonomy controls, and kill switch

## Non-Functional Requirements

- No plaintext platform credentials in storage or logs.
- Every generated document must be traceable to verified `ProfileFact` rows.
- Every submission attempt must create immutable audit events.
- Daily submission caps are enforced outside the scoring engine.
- Unsupported or unknown form fields cause skip, not guessed answers.
- A kill switch must stop queued and future automation work.

## Out of Scope For MVP

- LinkedIn or Indeed autonomous scraping
- Multi-user SaaS features
- Microservice extraction
- High-volume submission
- Freeform answers to arbitrary essay questions
