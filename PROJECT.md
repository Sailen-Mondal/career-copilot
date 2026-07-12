# Project: AI Career Copilot

## Architecture
The system is built as a Spring Boot modular monolith, talking to a separate TypeScript Playwright automation worker over Redis Streams.

### Core Modules (Spring Boot)
1. `com.careercopilot.profile`: Manages MasterProfile and ProfileFact.
2. `com.careercopilot.discovery`: Ingests jobs from Greenhouse API and performs deduplication.
3. `com.careercopilot.matching`: Computes match scores using embeddings and eligibility filters.
4. `com.careercopilot.generation`: Tailors resumes/cover letters and runs GroundednessVerifier.
5. `com.careercopilot.automation`: Interfaces with Redis Streams to enqueue form-filling tasks and handle responses.
6. `com.careercopilot.applications`: Manages overall application state, audit logs, and status updates.
7. `com.careercopilot.shared`: Contains security configurations (API key auth), common models, and database schema migrations.

### External Components
- **TypeScript Playwright Worker**: Reads application requests from Redis Stream, runs shadow form filling, uploads screenshots, and writes results back to Redis.
- **Frontend Dashboard**: Visualizes jobs, applications, match scores, logs, and contains the global kill switch.

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | E2E Testing Track Setup | Setup E2E testing framework, tiers 1-4 tests | None | COMPLETED |
| M2 | Core Backend & Discovery | Ingest from Greenhouse, composite key & embedding dedup, persist to DB, API Key Auth, Profile REST API | None | COMPLETED |
| M3 | Matching & Generation | Production & mock LlmClient/EmbeddingClient, Matcher, Tailored Generator & GroundednessVerifier | M2 | COMPLETED |
| M4 | Automation Engine | Redis Stream publisher/consumer, TypeScript Playwright shadow-worker, circuit breakers, rate limits | M3 | COMPLETED |
| M5 | Frontend Dashboard | HTML/JS frontend connected to REST APIs, status/audit logs, kill switch, autonomy slider | M4 | COMPLETED |

## Code Layout
- `app/src/main/java/com/careercopilot/` - Spring Boot backend Java files
- `app/src/test/java/com/careercopilot/` - Backend unit and integration tests
- `automation/` - TypeScript Playwright worker
- `frontend/` - Static HTML/JS files for dashboard
- `docs/` - System architecture and plans

## Interface Contracts

### profile ↔ matching / generation
- ProfileFact is read by both matching (for facts/preferences) and generation (for tailoring).
- `MasterProfile getProfile()`
- `List<ProfileFact> getProfileFacts(UUID profileId)`

### discovery ↔ matching
- `Job` data is passed to matching.
- `void processDiscoveredJob(Job job)`

### matching ↔ applications / automation
- Match scores and eligibility determine application routing.
- `MatchResult scoreJob(Job job, MasterProfile profile)`

### generation ↔ applications
- Generated documents are linked to applications.
- `GeneratedDocument generateTailoredDocument(Application application, DocumentType type)`

### applications ↔ automation (Redis Streams)
- Queue name: `cc:automation:jobs`
- Payload: JSON with `applicationId`, `jobUrl`, `tailoredResumePath`, `tailoredCoverLetterPath`
- Response Queue: `cc:automation:results`
- Response Payload: JSON with `applicationId`, `status` (SUCCESS/FAILED), `screenshotPath`, `logs` (array of strings)
