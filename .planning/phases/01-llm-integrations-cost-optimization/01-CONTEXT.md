# Phase 1: LLM Integrations & Cost Optimization - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning

<domain>
## Phase Boundary
Implement a robust and cost-minimized LLM integration strategy across the Career Copilot system (Job Discovery extraction, Borderline Job Matching, Form-Filling custom question handling, and ATS-optimized Resume/Cover Letter tailoring).
</domain>

<decisions>
## Implementation Decisions

### 1. Form-Filling & Answer Cache (Custom Questions)
- **Question Matching:** Use semantic similarity via embeddings (cosine similarity score >= 0.85) to determine if an incoming custom question has already been answered.
- **Cache Scope:** Implement a multi-tier cache. Questions will be classified as either "Generic" (global cache) or "Company-Specific" (company-scoped cache) to prevent cross-company context leakage.
- **Fallback Behavior:** If LLM generation is disabled, fails, or fails groundedness checks, the application is marked as `BLOCKED` (Skipped - Unsupported Field) on the dashboard for manual resolution. Never submit placeholders.

### 2. Job Matching (Borderline Evaluation)
- **Gate Logic:** The system will calculate the preliminary match score using cheap embeddings (0-50) and keyword intersection (0-50).
- **LLM Semantic Scoring:** If the preliminary score is between `60` and `85` (borderline), the system invokes the LLM for a deep semantic evaluation to adjust the score. Scores `< 60` are rejected immediately, and `> 85` are auto-approved, bypassing the LLM call entirely.

### 3. ATS-Optimized Resume Tailoring & Grounding
- **ATS Optimization:** The tailoring prompt will select and reword relevant skills and bullet points from the master profile to match the job description's context, rather than blindly copy-pasting keywords.
- **Primary-Secondary Chain:**
  1. *Primary Model:* Gemini 2.5 Flash drafts the tailored content.
  2. *Secondary Model:* Gemini 1.5 Pro performs a groundedness check, verifying all claims map back to UUIDs in `ProfileFact`.
  3. *Fallback:* If groundedness check fails, fallback to the original Master Profile resume document rather than submitting hallucinated details.

### 4. Java Chaining LLM Client
- Create a `ChainedLlmClient` interface and implementation in Spring Boot that wraps a primary `LlmClient` and handles fallback routing and validation.
</decisions>

<code_context>
## Existing Code Insights
- `LlmGenerationService.java` is already configured to inject `LlmClient` and generate resumes/cover letters.
- `MatchingService.java` scores jobs using cosine similarity of embeddings and keyword intersection.
- `JobDiscoveryService.java` currently uses regex-based extraction for skills/seniority.
</code_context>

<specifics>
## Specific Ideas
- Cache database table to store `question_text`, `question_embedding`, `answer_text`, `scope` (company name or null for global), and `created_at`.
</specifics>

<deferred>
## Deferred Ideas
- Multi-user authentication support.
</deferred>
