# E2E Test Infra: AI Career Copilot

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Job Ingestion & Dedup | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | Profile & Fact Management | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 3 | LLM & Embedding Clients | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 4 | Matching & Generation | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 5 | Automation Engine & Streams | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |
| 6 | Dashboard Frontend | ORIGINAL_REQUEST §R6 | 5 | 5 | ✓ |

## Test Architecture
- Test runner: JUnit 5 in `app/src/test/java/com/careercopilot/e2e/` run via `./gradlew test` (using Testcontainers for PostgreSQL and Redis).
- Test case format: REST calls using Spring's MockMvc/TestRestTemplate, passing HTTP header `X-API-Key` for authentication.
- Directory layout:
  - `app/src/test/java/com/careercopilot/e2e/`: contains all the E2E test files.
  - `app/src/test/resources/`: contains Greenhouse mock feed JSONs.
- Execution details:
  - The tests run without real Vertex AI API keys by setting `app.ai.client-type=mock`.
  - The Playwright worker and Redis stream are tested by subscribing to `cc:automation:jobs` and posting mock responses back to `cc:automation:results`.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Happy Path Flow | F1, F2, F3, F4, F5, F6 | High |
| 2 | Low Confidence Skip Flow | F2, F3, F4 | Medium |
| 3 | Company Blocklist Flow | F1, F3 | Medium |
| 4 | Hallucination Mitigation Flow | F2, F4 | High |
| 5 | Emergency Kill Switch Flow | F5, F6 | High |

## Coverage Thresholds
- Tier 1: ≥5 per feature (Total: 30)
- Tier 2: ≥5 per feature (where boundaries exist) (Total: 30)
- Tier 3: pairwise coverage of major feature interactions (Total: 6)
- Tier 4: ≥5 realistic application scenarios (Total: 5)
- **Total Minimum: 71 test cases**

---

## Detailed Test Cases

### Tier 1: Happy Path & Positive Cases (30 Test Cases)

#### Feature 1: Job Ingestion & Dedup (F1)
1. `testGreenhouseIngestionSuccess`: Verifies Greenhouse mock feed is parsed, and jobs are successfully saved to the database.
2. `testIngestionDeduplicationCompositeKey`: Verifies exact duplicate jobs (normalized company, title, location) update existing entries instead of inserting new ones.
3. `testIngestionDeduplicationEmbeddingSimilarity`: Verifies jobs matching high embedding similarity are recognized as duplicates and grouped or updated.
4. `testIngestionWithValidEmptyFeed`: Verifies ingestion of an empty feed processes without error and logs success with zero jobs saved.
5. `testPersistDiscoveredJobsProperties`: Verifies that all expected properties (title, description, location, company, url) are persisted correctly into the database.

#### Feature 2: Profile & Fact Management (F2)
6. `testCreateProfileSuccess`: Verifies that creating a new MasterProfile returns 201 Created and registers the profile.
7. `testGetProfileSuccess`: Verifies that querying an existing profile returns a 200 OK status code along with the correct details.
8. `testCreateProfileFactSuccess`: Verifies that adding a ProfileFact to an existing profile returns 201 Created.
9. `testGetProfileFactsSuccess`: Verifies that querying for all facts linked to a profile returns a 200 OK list of facts.
10. `testDeleteProfileFactSuccess`: Verifies that deleting a ProfileFact returns 204 No Content and successfully removes the fact from DB.

#### Feature 3: LLM & Embedding Clients (F3)
11. `testMockLlmClientGenerate`: Verifies that when `app.ai.client-type=mock`, calling the generation client yields the expected mock response.
12. `testMockEmbeddingClientGenerate`: Verifies that the mock embedding client yields deterministic embedding vectors.
13. `testLlmClientSelectionConfig`: Verifies that MockLlmClient is injected as a bean when client-type is set to mock.
14. `testEmbeddingClientSelectionConfig`: Verifies that MockEmbeddingClient is injected as a bean when client-type is set to mock.
15. `testMockLlmClientVerifyNoExternalNetworkCall`: Confirms that running the mock clients does not attempt external network requests to GCP APIs.

#### Feature 4: Matching & Generation (F4)
16. `testMatchingEngineEligibilityPreFilter`: Verifies that hard pre-filters (work authorization, seniority, locations) successfully exclude ineligible jobs.
17. `testMatchingEngineScoringSuccess`: Verifies that eligible jobs get evaluated and scored correctly based on embeddings and keywords.
18. `testResumeGenerationSuccess`: Verifies that resume tailoring generates text that strictly corresponds to ProfileFacts.
19. `testCoverLetterGenerationSuccess`: Verifies that cover letter tailoring generates text referencing correct ProfileFacts.
20. `testGroundednessVerifierPass`: Verifies that generated text containing correct `[fact:<UUID>]` markers passes verification.

#### Feature 5: Automation Engine & Streams (F5)
21. `testPublishJobToRedisStream`: Verifies that backend enqueues a form-filling job payload onto the Redis Stream `cc:automation:jobs`.
22. `testConsumeResultFromRedisQueue`: Verifies backend consumes from `cc:automation:results` queue and transitions application state.
23. `testAutonomyPolicySelection`: Verifies that the correct AutonomyPolicy (e.g. FULL/SHADOW/MANUAL) is selected based on configuration.
24. `testPlaywrightWorkerMessageRouting`: Verifies that form-filling jobs are correctly structured for consumption by the worker.
25. `testApplicationAuditLogUpdated`: Verifies that success response writes a proper audit log entry and changes application status to SUCCESS.

#### Feature 6: Dashboard Frontend (F6)
26. `testDashboardLoadStatus`: Verifies dashboard API returns active applications and correct statuses.
27. `testDashboardGetMatchScores`: Verifies match scores are fetched and rendered properly on client requests.
28. `testDashboardGetAuditLogs`: Verifies the audit logs endpoint returns the historical execution sequence.
29. `testDashboardKillSwitchTrigger`: Verifies that invoking the kill switch immediately updates the system state to halt execution.
30. `testDashboardAutonomySliderUpdate`: Verifies that the autonomy slider configuration updates in the backend.

---

### Tier 2: Boundary & Edge Cases (30 Test Cases)

#### Feature 1: Job Ingestion & Dedup (F1)
31. `testIngestionWithMalformedJson`: Verifies that feeding a corrupted feed JSON returns an HTTP 400 or logs an error gracefully.
32. `testIngestionDuplicateBoundarySimilarity`: Verifies edge cases where the embedding similarity score matches the dedup threshold exactly.
33. `testIngestionNullFields`: Verifies that jobs missing non-critical parameters (such as location or url) are handled without raising database exceptions.
34. `testIngestionHugePayload`: Verifies behavior under large batch feeds (e.g. 10k items) to check transaction batch size limits.
35. `testIngestionNetworkTimeoutFallback`: Verifies the fallback logic triggers if the external Greenhouse endpoint fails to respond.

#### Feature 2: Profile & Fact Management (F2)
36. `testCreateProfileMissingRequiredFields`: Verifies validation constraints on the MasterProfile request body.
37. `testGetProfileNonExistent`: Verifies querying a non-existent UUID profile returns 404 Not Found.
38. `testDeleteNonExistentProfileFact`: Verifies attempting to delete a non-existent ProfileFact returns 404 Not Found.
39. `testCreateFactMaxCharacterLimit`: Verifies saving a ProfileFact representing maximum permissible text length (e.g. 4000+ characters) persists successfully.
40. `testFactUpdateConcurrency`: Verifies optimistic/pessimistic locking handles simultaneous edits on the same ProfileFact.

#### Feature 3: LLM & Embedding Clients (F3)
41. `testLlmClientTimeoutBehavior`: Verifies that mock client simulations of a network timeout trigger configured resilience retry policies.
42. `testLlmClientRateLimitExceeded`: Verifies that simulating an HTTP 429 response from the LLM client triggers backoff and circuit breaker.
43. `testEmbeddingClientNullInput`: Verifies that calling embedding generation with null or empty text throws an appropriate validation exception.
44. `testLlmClientTokenLengthLimit`: Verifies that passing input exceeding maximum context length limits returns a descriptive error or handles truncation.
45. `testLlmClientInvalidModelConfiguration`: Verifies that configuring unsupported model settings is detected on startup.

#### Feature 4: Matching & Generation (F4)
46. `testMatchingEngineZeroSimilarity`: Verifies that jobs having completely irrelevant contents yield a match score of 0.0.
47. `testGenerationFactMarkerMissing`: Verifies that a generated document missing `[fact:<UUID>]` markers fails the groundedness verifier.
48. `testGroundednessVerifierHallucinatedFact`: Verifies that text containing `[fact:<invalid-uuid>]` fails validation.
49. `testGenerationEmptyProfileFacts`: Verifies that attempting to generate tailored documents when the profile has no facts returns a validation error.
50. `testGroundednessVerifierFormatError`: Verifies that poorly formatted fact markers (e.g. missing braces or uuid format errors) fail verification.

#### Feature 5: Automation Engine & Streams (F5)
51. `testRedisStreamConnectionFailure`: Verifies that the queue consumer handles brief Redis disconnections and auto-reconnects.
52. `testRateLimiterTriggered`: Verifies that exceeding rate limits queues/delays job submission requests.
53. `testCircuitBreakerOpensOnFailures`: Verifies that consecutive automation failures trip the circuit breaker.
54. `testPlaywrightWorkerTimeout`: Verifies that worker execution taking longer than the configured timeout transitions the job state to FAILED.
55. `testAutonomyPolicyRejectAutomation`: Verifies jobs with match scores below threshold are routed to MANUAL_REVIEW.

#### Feature 6: Dashboard Frontend (F6)
56. `testDashboardGetLogsEmpty`: Verifies querying audit logs for a brand new application returning empty lists behaves correctly.
57. `testKillSwitchDuringActiveStream`: Verifies that enabling the kill switch with actively executing jobs cancels in-flight stream processing.
58. `testSliderBoundaryValues`: Verifies setting the score slider threshold to exactly 0.0 or 1.0 behaves correctly.
59. `testDashboardUnauthorizedAccess`: Verifies that sending requests to dashboard APIs without the `X-API-Key` header returns 401 Unauthorized.
60. `testDashboardInvalidApiKey`: Verifies that sending requests with an invalid api-key header returns 403 Forbidden.

---

### Tier 3: Pairwise Feature Interactions (6 Test Cases)

61. `testInteraction_F1_F3_F4`: Tests Greenhouse Job Ingestion (F1) -> Embedding similarity check (F3) -> Job Match Scoring (F4).
62. `testInteraction_F2_F4_F5`: Tests Fact Management (F2) -> Resume Tailoring and Groundedness Verification (F4) -> Async Stream Enqueue (F5).
63. `testInteraction_F4_F5_F6`: Tests Match Score Evaluation (F4) -> Autonomy Policy checks for dispatching to Stream (F5) -> Dashboard live state updates (F6).
64. `testInteraction_F1_F2_F4`: Tests Ingested job constraints (F1) against Profile Facts (F2) during Matcher pre-filtering (F4).
65. `testInteraction_F5_F6_KillSwitch`: Tests that an active, running Redis stream job (F5) is instantly canceled/halted by triggering the frontend kill switch (F6).
66. `testInteraction_F3_F4_Groundedness`: Tests that a Mock LLM response containing a simulated hallucination (F3) is detected by the GroundednessVerifier (F4), blocking stream submission (F5).

---

### Tier 4: Real-World Application Scenarios (5 Test Cases)

67. **Scenario 1: Happy Path Flow**
    - *Complexity*: High
    - *Features*: F1, F2, F3, F4, F5, F6
    - *Sequence*:
      1. Ingest jobs via Greenhouse mock feed.
      2. Fetch and register a candidate profile with verified facts.
      3. Run matcher; job receives a score above the autonomy threshold.
      4. Tailor resume and cover letter; verify they pass the GroundednessVerifier.
      5. Submit form-filling job to `cc:automation:jobs` stream.
      6. Mock worker responds to `cc:automation:results` with SUCCESS and a screenshot path.
      7. Verify Dashboard displays status as success and contains appropriate logs.

68. **Scenario 2: Low Confidence Skip Flow**
    - *Complexity*: Medium
    - *Features*: F2, F3, F4
    - *Sequence*:
      1. Ingest job with requirements not matching the candidate's profile facts.
      2. Match score is calculated below the autonomy threshold.
      3. AutonomyPolicy halts automation and updates application status to MANUAL_REVIEW.
      4. Verify no job is posted to the automation queue.

69. **Scenario 3: Company Blocklist Flow**
    - *Complexity*: Medium
    - *Features*: F1, F3
    - *Sequence*:
      1. Add a target company name to the user's blocklist profile.
      2. Ingest jobs matching the blocklisted company.
      3. Matcher pre-filter flags the job as ineligible.
      4. Verify job is marked as ineligible and skipped, with no scoring or generation performed.

70. **Scenario 4: Hallucination Mitigation Flow**
    - *Complexity*: High
    - *Features*: F2, F4
    - *Sequence*:
      1. Run resume tailoring where the LLM response contains a claim not present in the ProfileFact table.
      2. The GroundednessVerifier detects the ungrounded claim.
      3. Generation fails, and the application status transitions to REJECTED/MANUAL_REVIEW.
      4. Verify no form-filling is dispatched to the stream.

71. **Scenario 5: Emergency Kill Switch Flow**
    - *Complexity*: High
    - *Features*: F5, F6
    - *Sequence*:
      1. Enqueue multiple application tasks in the automation stream.
      2. Call the frontend kill switch API (`/api/automation/kill-switch`).
      3. Verify the stream consumer stops processing immediately.
      4. Verify pending/in-flight tasks transition to HALTED state.
