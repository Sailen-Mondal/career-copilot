# Original User Request

## Initial Request — 2026-07-08T21:49:24Z

An AI Career Copilot designed to automate job discovery, matching, tailored resume/cover letter generation, and browser-automation form-filling with safety-oriented guardrails (tiered autonomy, groundedness checking, and rate limits).

Working directory: c:/Users/saile/Documents/Codex/2026-07-07/files-mentioned-by-the-user-ai/outputs/career-copilot
Integrity mode: demo

## Architecture & Code Constraints
1. **Monolith first**: Follow the modular monolith layout in Plan_v2.md (c:/Users/saile/Documents/Codex/2026-07-07/files-mentioned-by-the-user-ai/outputs/career-copilot/docs/Plan_v2.md).
2. **Integrity Rule**: Prohibit copying core business logic, matching algorithms, prompt logic, or workflows from third-party repositories. Established libraries/frameworks (Spring Boot, Spring AI, React, Flyway, Testcontainers, Playwright) and project tooling (Gradle, npm, Flyway, Docker/Testcontainers, Git) are fully allowed. Reading test source code before implementing is allowed.
3. **No Placeholders**: Avoid introducing empty stubs. Mark any placeholders or TODOs with a clear explanation and acceptance criteria.
4. **Stable Public Interfaces**: Keep public interfaces stable unless changes are documented and justified.

## Requirements

### R1. Backend Job Ingestion and Dedup
- Implement ingestion from the Greenhouse public job board API (with a mock JSON feed backup for local test execution).
- Deduplicate discovered jobs using a composite key (normalized company + title + location) and embedding similarity.
- Persist discovered jobs into the database.

### R2. Master Profile and Grounded Fact Management
- Expose secure REST API endpoints (authenticated via the existing API key filter) for managing the MasterProfile and associated ProfileFact records.

### R3. Hybrid LLM and Embedding Client Setup
- Define clean interfaces for LLM interaction (LlmClient) and embedding generation (EmbeddingClient).
- Implement the Production clients using Spring AI's Vertex AI integration (Gemini models and Vertex AI embeddings).
- Implement Mock/Local implementations (MockLlmClient, MockEmbeddingClient) for local development and CI testing to prevent API quota consumption and ensure deterministic test results.

### R4. AI Matching and Grounded Resume/Cover Letter Generation
- Implement a matching engine that evaluates jobs against the MasterProfile using embeddings (pgvector) and keyword matching, applying hard pre-filters (work authorization, seniority, locations) before scoring.
- Implement resume and cover letter tailoring utilizing the LlmClient. The generation process must strictly select and reword facts from ProfileFact rows and output text containing [fact:<UUID>] markers corresponding to those facts.
- Execute the GroundednessVerifier on generated text to block any submissions containing hallucinated claims or missing markers.

### R5. Asynchronous Automation Engine via Redis Streams
- Decouple the Spring Boot backend and Playwright worker:
  - Spring Boot app publishes form-filling jobs to a Redis Stream.
  - TypeScript Playwright worker in automation/ reads from the Redis Stream, fills the job application form in shadow mode (never clicks the final submit button), takes a screenshot, and posts the results back to a Redis Result Queue.
  - Spring Boot app consumes from the Redis Result Queue and updates the application status and audit logs.
- Support tiered autonomy policies (using AutonomyPolicy.java) and platform rate limiting/circuit breakers.

### R6. Dashboard Frontend
- Connect the HTML/JS frontend to the Spring Boot backend APIs.
- Display application statuses, match scores, and detailed audit trails.
- Provide a global kill switch to halt all automation and a slider to adjust the autonomy match score threshold.

## Verification Plan

### Automated Tests
- Run backend tests using ./gradlew test (using Testcontainers for Postgres/Redis). All unit, integration, and schema verification tests must pass.
- Write unit/integration tests for new services, controllers, and clients.

### Manual Verification
- Launch the backend, Redis, and Playwright worker locally.
- Ingest a sample job feed and trigger a shadow run from the frontend dashboard.
- Verify that form-filling runs asynchronously, produces a local screenshot, and logs the detailed steps on the frontend table.

## Acceptance Criteria

### Backend & Database Ingestion
- [ ] Flyway database migrations run successfully on startup.
- [ ] Discovered jobs are parsed, deduplicated, and persisted correctly.
- [ ] The REST APIs for profile management and application query work correctly.

### AI Matching & Generation
- [ ] Matching engine correctly filters jobs based on eligibility before scoring.
- [ ] Resume/cover letter generation correctly integrates mock and real Vertex AI clients.
- [ ] Groundedness verifier successfully rejects generated content that contains ungrounded claims or lacks appropriate fact markers.

### Asynchronous Playwright Automation
- [ ] Form filling runs asynchronously over Redis Stream.
- [ ] The Playwright worker completes shadow form filling, captures a screenshot, and uploads/returns the screenshot path.
- [ ] Platform circuit breakers and rate limits correctly block/skip runs if caps are exceeded or failure rates spike.

### Frontend
- [ ] The frontend displays live application data, logs, and status from the backend.
- [ ] Clicking the global kill switch immediately halts all running and queued tasks.
