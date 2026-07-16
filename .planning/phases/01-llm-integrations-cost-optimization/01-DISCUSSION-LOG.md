# Phase 1 Discussion Log

**Date:** 2026-07-16
**Orchestrator:** Antigravity

## Areas Discussed

### Form-Filling & Answer Cache
- **Options presented:**
  - Embedding Cosine Similarity (A)
  - Exact String Matching (B)
  - Jaccard/Keyword matching (C)
- **User Selection:** Option A (Embedding Cosine Similarity) with multi-tier caching (generic vs. company-specific) and skip & flag fallback.

### Job Matching
- **Decision:** Integrate LLM semantic check for borderline cases (preliminary score between 60 and 85). Bypass LLM for obvious matches/mismatches.

### Resume Generation
- **Decision:** Apply ATS score optimization by tailoring bullets to JD context, using a primary-secondary model chain (Gemini 2.5 Flash -> Gemini 1.5 Pro check) with fallback to Master Profile.

## Deferred Ideas
- Dynamic generation of entire new resume formats.
