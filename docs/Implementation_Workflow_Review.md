# AI Career Copilot Implementation and Workflow Review

Date: 2026-07-11

## Review Scope

This review was requested before making any fixes. I read the Markdown documentation first, then compared the intended project workflow against the Java backend, TypeScript automation worker, frontend dashboard, database migrations, and tests.

No implementation fixes were made. The only project change from this review is this report file.

## Project Understanding

The project is an AI Career Copilot. Its intended MVP workflow is:

1. Ingest jobs from safe/public sources such as Greenhouse.
2. Normalize, deduplicate, and persist jobs.
3. Store a master profile and verified profile facts.
4. Score jobs against the profile using eligibility filters, embeddings, and skills.
5. Generate tailored resume and cover-letter content using only verified profile facts.
6. Block generation or submission if groundedness verification fails.
7. Apply autonomy policy gates: threshold, groundedness, unsupported fields, daily caps, circuit breakers, blocklists, and kill switch.
8. Queue browser automation work through Redis Streams.
9. Have the Playwright worker fill forms in shadow mode, capture screenshots, and return structured results.
10. Update applications and audit logs so the frontend dashboard shows live state and lets the user halt automation.

The docs also describe the repository as a walking skeleton, not a full product. That framing is important: many core components exist, but the system is not yet a complete guarded workflow.

## Verification Run

### Backend

Command run:

```powershell
.\gradlew.bat test
```

Result: failed before Java compilation and before any tests could execute.

Failure summary:

```text
Could not resolve org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter:1.0.0
Could not GET https://repo.spring.io/release/... Received status code 401
```

Relevant files:

- `gradle/libs.versions.toml:7` sets `spring-ai = "1.0.0"`.
- `gradle/libs.versions.toml:25` defines `spring-ai-vertex-ai-gemini-spring-boot-starter`.
- `app/build.gradle.kts:37` imports that dependency.
- `settings.gradle.kts:12-13` includes Spring milestone and release repositories.

Impact: the backend cannot currently be verified by the intended test command. Until this is fixed, no backend implementation claim can be considered proven by the test suite.

### Automation Worker

Command run:

```powershell
npm run build
```

Working directory:

```text
automation/
```

Result: passed. TypeScript compiled successfully.

## Overall Workflow Verdict

The project has several useful building blocks, but the whole system does not yet work as one end-to-end guarded career-copilot workflow.

Most important: the intended chain `ingest -> match -> generate -> verify -> autonomy policy -> Redis automation -> result/audit update -> dashboard` is not implemented as a connected workflow. The modules are mostly separate APIs/classes, and the safest gates are not enforced before an application enters automation.

If judged as a walking skeleton, it is a meaningful start. If judged against the requested MVP/acceptance workflow, it is not ready to deliver the expected behavior.

## Critical Findings

### 1. Backend cannot build or run tests because dependency resolution fails

Severity: Blocker

Evidence:

- `.\gradlew.bat test` fails resolving `org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter:1.0.0`.
- Dependency configured at `gradle/libs.versions.toml:25`.
- Used by `app/build.gradle.kts:37`.

Why this matters:

The project cannot currently prove that the backend compiles, that Flyway/Hibernate validation works, or that any integration test passes. This blocks confidence in every backend module.

Expected:

The standard verification command in `ORIGINAL_REQUEST.md` should run backend tests successfully.

Actual:

The build stops before compile/test execution.

### 2. The main workflow orchestrator is missing

Severity: Critical

Evidence:

- `ApplicationService.createApplication(...)` at `app/src/main/java/com/careercopilot/applications/ApplicationService.java:38` accepts caller-provided `jobId`, `profileId`, `matchScore`, and breakdown.
- It immediately sets `AutomationTier.AUTO` at `ApplicationService.java:48`.
- It immediately sets status `QUEUED` at `ApplicationService.java:49`.
- `AutonomyPolicy.decide(...)` exists at `app/src/main/java/com/careercopilot/automation/AutonomyPolicy.java:9`, but search shows it is not called by any production workflow.
- `AutomationPublisher.publish(...)` exists at `app/src/main/java/com/careercopilot/automation/AutomationPublisher.java:45`, but search shows it is not called by any production workflow.

Why this matters:

The application workflow currently trusts API input instead of computing and enforcing the required gates. A client can create an application with any match score, and the backend records it as `AUTO` and `QUEUED` without:

- scoring the job,
- checking eligibility,
- generating documents,
- verifying groundedness,
- checking daily caps,
- checking unsupported fields,
- checking circuit breakers,
- checking the kill switch for the application decision,
- publishing a Redis automation command.

Expected:

Creating or advancing an application should be driven by backend-controlled workflow logic.

Actual:

The backend exposes separate pieces, but no service coordinates them into the documented pipeline.

### 3. Groundedness verification is not strong enough to prevent hallucinated or unmarked claims

Severity: Critical

Evidence:

- `GroundednessVerifier.verify(...)` only checks source fact IDs, fact markers, and literal `unverified:` text at `app/src/main/java/com/careercopilot/generation/GroundednessVerifier.java:17-44`.
- It does not extract factual claims from generated text.
- It does not require every sentence or claim to have a marker.
- The test `passes_whenDocumentHasNoMarkersAndNoFacts` explicitly accepts a generated document with no fact markers and no source facts at `app/src/test/java/com/careercopilot/generation/GroundednessVerifierTest.java:116-128`.
- `LlmGenerationService` loads all profile facts at `app/src/main/java/com/careercopilot/generation/LlmGenerationService.java:53`, then puts all fact IDs into `sourceFactIds` at `LlmGenerationService.java:57` and `LlmGenerationService.java:97`, regardless of which facts the model actually used.

Why this matters:

The docs say generated resumes and cover letters must be traceable to verified `ProfileFact` rows and must block missing markers/hallucinations. Current verification can pass content that has no markers, or pass a document whose `sourceFactIds` list was filled by the service rather than proven from the content.

Expected:

The verifier should reject missing markers and claims that cannot be traced to available profile facts.

Actual:

The verifier validates marker bookkeeping, not factual groundedness.

### 4. Generated documents are not persisted or linked back to applications

Severity: Critical

Evidence:

- The database has `generated_document` at `app/src/main/resources/db/migration/V1__initial_schema.sql:51`.
- Applications can reference generated documents at `V1__initial_schema.sql:83` and `V1__initial_schema.sql:87`.
- Code has a `GeneratedDocument` record at `app/src/main/java/com/careercopilot/generation/GeneratedDocument.java:7`.
- There is no JPA entity/repository/service that persists generated documents.
- `GenerationController` returns the document directly at `app/src/main/java/com/careercopilot/generation/GenerationController.java:38-44`.
- `ApplicationEntity` has `resumeVersionId`, `coverLetterVersionId`, `groundednessCheckPassed`, and `groundednessReport` fields at `app/src/main/java/com/careercopilot/applications/ApplicationEntity.java:33-65`, but generation does not update them.

Why this matters:

Automation commands need resume/cover-letter document IDs, and the dashboard/audit workflow needs durable generated artifacts. Right now generation is transient and disconnected from application state.

Expected:

Generation should persist documents, update the application, record groundedness results, and only then allow automation routing.

Actual:

Generation returns a verified object to the API caller but does not update durable workflow state.

### 5. Automation Redis publishing exists but no backend path triggers it

Severity: Critical

Evidence:

- `AutomationPublisher.publish(...)` is implemented at `app/src/main/java/com/careercopilot/automation/AutomationPublisher.java:45-56`.
- No controller or application service calls it.
- The app has no endpoint like "queue this application for automation."

Why this matters:

The Playwright worker can consume Redis stream messages, but the backend never emits them as part of the application workflow. Therefore the promised asynchronous form-filling flow cannot be reached through normal backend usage.

Expected:

After match/generation/groundedness/autonomy gates pass, the backend should publish an `AutomationCommand`.

Actual:

Publishing is an unused service method.

### 6. Kill switch behavior can drop audit results and does not immediately stop queued work

Severity: Critical

Evidence:

- `KillSwitchService.halt()` only sets an in-memory boolean and appends a HALT sentinel to the jobs stream at `app/src/main/java/com/careercopilot/automation/KillSwitchService.java:38-43`.
- `AutomationResultConsumer.processMessage(...)` skips result processing when halted at `app/src/main/java/com/careercopilot/automation/AutomationResultConsumer.java:133-135`.
- The polling loop acknowledges the stream message after `processMessage(...)` at `AutomationResultConsumer.java:114`.
- The TypeScript worker stops when it eventually reads a HALT message at `automation/src/redis-consumer.ts:122-126`.

Why this matters:

If the kill switch is active while a worker result arrives, the backend skips it and then acknowledges it. That loses audit/status information. Also, appending HALT after already queued jobs does not guarantee pending jobs before HALT are canceled immediately.

Expected:

The kill switch should prevent new and queued automation work while preserving result/audit events from already-running work.

Actual:

It can discard result events and does not clear or reclassify already queued jobs.

## High-Risk Findings

### 7. Matching is incomplete for the documented eligibility workflow

Severity: High

Evidence:

- Matching pre-filters currently check work authorization, blocklist, and job status at `app/src/main/java/com/careercopilot/matching/MatchingService.java:70-90`.
- The docs require work authorization, seniority, and locations as hard filters.
- Ingestion leaves required skills empty at `app/src/main/java/com/careercopilot/discovery/JobDiscoveryService.java:111`.
- Ingestion leaves seniority, salary, and work authorization as `null` at `JobDiscoveryService.java:112-114`.
- Matching keyword score uses `job.getRequiredSkills()` at `MatchingService.java:119-129`; for ingested jobs this will normally be empty, so keyword scoring contributes nothing.

Why this matters:

The intended matching workflow will not reliably block jobs based on seniority/location/sponsorship, and ingested jobs do not provide the fields needed for full scoring.

Expected:

Ingestion should extract/normalize the fields matching depends on, and matching should apply all documented hard filters before scoring.

Actual:

Most eligibility and keyword dimensions are empty or not checked.

### 8. Frontend and backend API contracts do not align

Severity: High

Evidence:

- Docs define `POST /api/sources/greenhouse/sync` at `docs/API.md:17`, but backend implements `/api/jobs/sync` through `GreenhouseSyncController`.
- Docs define `/api/policy` at `docs/API.md:27-31`, but no policy controller exists.
- Docs define `/api/automation/pause` and `/api/automation/resume` at `docs/API.md:35-39`, while backend uses `/api/automation/kill-switch` at `app/src/main/java/com/careercopilot/automation/AutomationController.java:46`.
- Frontend calls `/api/automation/shadow-mode` at `frontend/src/app.js:541`, but backend has no such endpoint.
- Frontend calls `/api/profile/default/autonomy-threshold` at `frontend/src/app.js:562`, but backend has no such endpoint.

Why this matters:

The dashboard can look functional because it has mock fallbacks, but several controls will not persist or affect backend state.

Expected:

Docs, frontend, and backend should agree on API routes.

Actual:

Several routes are missing or renamed.

### 9. Dashboard application data shape does not match backend application data

Severity: High

Evidence:

- Frontend table expects `company`, `title`, `reason`, `matchScore`, `status`, and `submittedAt` at `frontend/src/app.js:320-332`.
- Backend `ApplicationEntity` exposes `jobId`, `profileId`, `matchScore`, `status`, and timestamps, but not company/title/reason at `app/src/main/java/com/careercopilot/applications/ApplicationEntity.java:25-52`.

Why this matters:

If the dashboard successfully fetches real backend applications, company, role, and reason will show blank/default values. That hides the main workflow information the user needs to audit.

Expected:

`GET /api/applications` should return a dashboard DTO joined with job details and skip/failure reason.

Actual:

It returns raw application entities.

### 10. Production embedding client is missing

Severity: High

Evidence:

- `EmbeddingClient` is defined at `app/src/main/java/com/careercopilot/discovery/EmbeddingClient.java:3`.
- Only `MockEmbeddingClient` implements it at `app/src/main/java/com/careercopilot/discovery/MockEmbeddingClient.java:6`.
- `AiClientConfig` registers only the mock embedding client at `app/src/main/java/com/careercopilot/shared/AiClientConfig.java:33-35`.
- A production Vertex LLM client exists, but no production Vertex embedding client appears in production code.

Why this matters:

Requirement R3 asks for production and mock LLM/embedding clients. Without a production embedding implementation, production matching/dedup cannot use real embeddings.

Expected:

`app.ai.client-type=vertex` should provide both LLM and embedding clients.

Actual:

It appears to provide only the LLM client.

### 11. pgvector Java mapping/query binding is likely unproven and risky

Severity: High

Evidence:

- Schema uses `embedding_vector VECTOR(1536)` at `app/src/main/resources/db/migration/V1__initial_schema.sql:48`.
- `JobEntity` maps it as plain `float[]` at `app/src/main/java/com/careercopilot/discovery/JobEntity.java:76-77`.
- Native query casts a `float[]` parameter to vector at `app/src/main/java/com/careercopilot/discovery/JobRepository.java:15-16`.
- No pgvector Java/Hibernate helper dependency is present in `gradle/libs.versions.toml`.
- Integration tests intended to verify this could not run because the backend build is blocked.

Why this matters:

Hibernate/Postgres may not correctly map a Java `float[]` to the pgvector column or bind it into `CAST(:embedding AS vector)`. If that fails, deduplication and matching persistence fail.

Expected:

Vector persistence and nearest-neighbor query should be verified by tests.

Actual:

The tests could not run, and the current mapping is a risk area.

### 12. Automation worker uses stub candidate data instead of generated documents/profile facts

Severity: High

Evidence:

- `buildStubResumeData(...)` is explicitly stubbed at `automation/src/shadow-worker.ts:34-54`.
- The worker fills identity/contact/cover-letter fields from generated placeholder values based on IDs, not from verified profile facts or generated document content.

Why this matters:

Even in shadow mode, the screenshot and field report are not proving that real generated materials would be applied correctly. This weakens the safety validation loop.

Expected:

The worker should receive or fetch real, verified profile/document data.

Actual:

It uses deterministic fake data.

### 13. Frontend likely falls back to mocks in normal static-file use

Severity: High

Evidence:

- `frontend/README.md` says to open `index.html` directly.
- Frontend calls `http://localhost:8080` with custom `X-API-Key` header at `frontend/src/app.js:6`.
- Fetch helpers silently return mock data if requests fail at `frontend/src/app.js:185-213`.
- Backend security config has no visible CORS configuration in `app/src/main/java/com/careercopilot/shared/SecurityConfig.java`.

Why this matters:

Opening the static file directly may trigger browser CORS/preflight failures, then the app silently displays mock applications/events. A user could think the dashboard is live when it is not.

Expected:

Dashboard should clearly distinguish live data from mock fallback, and backend should support the intended local frontend origin.

Actual:

Fallback is silent except console warnings.

## Medium-Risk Findings

### 14. Request validation gaps can cause 500s or invalid state

Severity: Medium

Evidence:

- `ProfileController.createOrUpdateProfile(...)` validates only daily cap and threshold at `app/src/main/java/com/careercopilot/profile/ProfileController.java:31-36`.
- `CreateProfileRequest.workAuthorization` can be null at `app/src/main/java/com/careercopilot/profile/CreateProfileRequest.java:6`.
- `CreateFactRequest.type` and `bulletText` can be null at `app/src/main/java/com/careercopilot/profile/CreateFactRequest.java:7-12`.
- `ApplicationController.updateStatus(...)` calls `ApplicationStatus.valueOf(...)` directly at `app/src/main/java/com/careercopilot/applications/ApplicationController.java:56`.

Why this matters:

Bad input can become database constraint errors or uncaught exceptions instead of clean 400 responses. This makes APIs brittle and harder for the frontend to handle.

Expected:

Required fields and enum values should be validated at the API boundary.

Actual:

Some invalid input can reach domain/database code.

### 15. Applications can reference a profile ID without a database foreign key

Severity: Medium

Evidence:

- `V3__application_entity_columns.sql` adds `profile_id UUID` at `app/src/main/resources/db/migration/V3__application_entity_columns.sql:3`.
- It does not add a foreign key to `master_profile`.
- `ApplicationService.createApplication(...)` does not validate `profileId` exists before saving.

Why this matters:

Applications can be created for nonexistent profiles. That breaks generation/matching traceability.

Expected:

Application profile IDs should either be foreign-key constrained or validated by service logic.

Actual:

They are neither.

### 16. Ingestion error handling can hide serious failures

Severity: Medium

Evidence:

- `JobDiscoveryService.syncBoard(...)` catches `Exception` for each job and logs it, then continues.
- If every job fails, the controller still returns HTTP 200 with an empty list.
- `RestGreenhouseClient` has no visible timeout/backoff/fallback behavior.

Why this matters:

A broken feed, vector mapping issue, database write failure, or parser bug can look like "0 new jobs" instead of a failed sync.

Expected:

Sync should distinguish "no jobs found" from "sync failed."

Actual:

Errors can be swallowed into an apparently successful response.

### 17. HTML cleaning is too weak for the prompt-injection risk described in the docs

Severity: Medium

Evidence:

- `HTMLStripper.stripHtml(...)` removes tags with a regex at `app/src/main/java/com/careercopilot/discovery/HTMLStripper.java:5-12`.
- Docs explicitly call out stripping invisible/off-screen text before prompting at `docs/Plan_v2.md:236-238`.

Why this matters:

Regex tag removal does not reliably remove script/style content, hidden text, CSS-hidden content, malicious prompt-injection sections, or malformed HTML. Job descriptions are untrusted input and later feed matching/generation prompts.

Expected:

HTML sanitization should be parser-based and should remove invisible/script/style content.

Actual:

It is a basic regex stripper.

### 18. Duplicate ingestion currently skips rather than updates/refreshes existing jobs

Severity: Medium

Evidence:

- Existing dedup key match causes `continue` at `app/src/main/java/com/careercopilot/discovery/JobDiscoveryService.java:58-62`.
- The Greenhouse ingestion test expects the second sync to return no new jobs.
- The broader requirements mention deduplication and persistence; detailed test infra also describes duplicate jobs updating existing entries.

Why this matters:

If an existing job changes title, content, sponsorship metadata, status, or URL, the current sync path will not refresh the stored job.

Expected:

Dedup should avoid duplicate rows while still updating freshness/status/content where appropriate.

Actual:

Duplicates are skipped.

### 19. Circuit breaker state is modeled but not integrated

Severity: Medium

Evidence:

- `CircuitBreakerState` exists at `app/src/main/java/com/careercopilot/automation/CircuitBreakerState.java`.
- `circuit_breaker_state` table exists in `V1__initial_schema.sql`.
- `AutonomyPolicy` accepts `candidate.circuitBreakerOpen()` at `app/src/main/java/com/careercopilot/automation/AutonomyPolicy.java:16-18`.
- No repository/service appears to record failures, compute rolling error windows, or feed circuit breaker state into application decisions.

Why this matters:

Circuit breakers are one of the main replacements for manual approval. If not integrated, automation cannot self-pause after repeated failures.

Expected:

Worker failures should update breaker state, and application decisions should read current breaker state.

Actual:

Breaker logic is represented as a boolean input but not operational.

### 20. Test coverage is much smaller than the documented plan and misses the system workflow

Severity: Medium/High

Evidence:

- `TEST_INFRA.md` describes a minimum of 71 test cases.
- Current tests cover selected pieces: auth filter, schema, profile CRUD, Greenhouse mock ingestion, dedup query, groundedness verifier, autonomy policy.
- There is no end-to-end test for `profile -> ingest -> match -> generate -> groundedness -> autonomy -> Redis publish -> result consume -> dashboard data`.
- Backend tests could not run due dependency resolution.

Why this matters:

The highest-risk area is the workflow, and that is the least covered part.

Expected:

At least one opaque-box happy path and one blocked/groundedness-failure path should be executable.

Actual:

Only isolated module tests exist, and they are currently blocked from execution.

## Positive Findings

- The repo structure follows the documented modular monolith plus separate automation worker.
- API-key security exists and is covered by a unit test.
- Flyway migrations define the core planned tables, including profiles, jobs, generated documents, applications, automation events, and circuit breaker state.
- Greenhouse mock ingestion exists and is defaulted for local/offline use.
- Composite-key deduplication and semantic duplicate query paths are represented.
- Autonomy policy logic has a clean pure-function style and unit coverage.
- The TypeScript worker builds successfully.
- The Playwright worker is conservative about shadow mode and logs submit buttons instead of clicking them.
- The frontend is visually complete and polls backend endpoints with fallback behavior.

## Expected Workflow vs Current Workflow

| Workflow stage | Expected | Current status |
|---|---|---|
| Greenhouse ingestion | Fetch, normalize, dedup, persist | Partially implemented |
| Job enrichment | Skills, seniority, location, work auth, salary | Mostly missing |
| Profile/facts | REST CRUD for master profile and facts | Partially implemented |
| Matching | Eligibility pre-filter plus embedding/keyword score | Partially implemented |
| Generation | Use only verified facts and job context | Partially implemented |
| Groundedness | Block missing markers and hallucinated claims | Insufficient |
| Application lifecycle | Own workflow state and audit trail | Partially implemented |
| Autonomy policy | Enforced before queueing automation | Not integrated |
| Redis job publishing | Publish only after gates pass | Not integrated |
| Worker shadow fill | Fill with real verified document/profile data | Stub data only |
| Result consuming | Update status and preserve audit logs | Partially implemented; drops results while halted |
| Frontend | Live status, controls, audit trail | Partially connected; several endpoint mismatches |
| Kill switch | Stop queued and future work immediately | Incomplete |

## Highest-Priority Fix Order For Later

Do not implement these until explicitly requested. Suggested order:

1. Unblock the backend build dependency resolution.
2. Add a single workflow/orchestration service for application processing.
3. Persist generated documents and link them to applications.
4. Strengthen groundedness verification so missing markers and unsupported claims fail.
5. Wire autonomy policy into the application workflow before Redis publishing.
6. Add an API path that queues automation only after all gates pass.
7. Fix kill switch semantics so queued work halts and result/audit messages are preserved.
8. Align backend, frontend, and docs API contracts.
9. Return dashboard DTOs with job/company/title/reason instead of raw application entities.
10. Add production embedding client and verify pgvector mapping.
11. Expand tests to cover at least one full happy path and one blocked safety path.

## Final Assessment

The project is a solid early skeleton, but it is not yet a reliable end-to-end AI Career Copilot. The largest issue is not one isolated bug; it is that the workflow guardrails exist as separate parts rather than as mandatory gates in the system path.

The most important product risk is that the current backend can record an application as queued/auto without proving match quality, groundedness, policy approval, generated documents, or automation readiness. That means the system currently cannot deliver the expected guarded automation workflow.

