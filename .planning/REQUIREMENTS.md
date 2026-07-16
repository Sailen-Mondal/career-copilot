# Requirements

## v1 Requirements

### LLM Ingestion & Extraction (Discovery)
- [ ] **LLM-INGEST-01**: The system must use an LLM to extract metadata (skills, seniority, work authorization) from job descriptions when regex-based heuristics are ambiguous or yield low-confidence results.

### LLM Matching (Matching)
- [ ] **LLM-MATCH-01**: The system must invoke an LLM for semantic evaluation only on borderline matches (e.g. score between 60 and 85). Clear rejections (<60) and clear matches (>85) must bypass LLM scoring to save tokens.

### LLM Form Filling (Automation)
- [ ] **LLM-AUTO-01**: The system must answer custom form questions by first checking a local pre-approved answer bank, then calling an LLM if unmatched, and falling back to skipping the application if LLM generation fails or is disabled.

### LLM Tailoring & Grounding (Generation)
- [ ] **LLM-TAILOR-01**: The system must generate tailored resume updates and cover letters using a primary-secondary chain: a primary cheap model generates the content, a secondary model validates groundedness against master profile facts, and a fallback returns the untailored master document if validation fails.

### Cost Control & Cache Optimization
- [ ] **LLM-COST-01**: The system must cache all LLM and embedding outputs (e.g. embedding vectors, generated form answers) to prevent duplicate calls and minimize API token consumption.

### Model Chain Framework (Chaining)
- [ ] **LLM-CHAIN-01**: Implement a configurable LLM Chaining framework that supports routing calls to a primary model (cheap), secondary model (premium, for validation/error recovery), and a static/local fallback.

## Out of Scope
- Unlimited LLM generation without cost caps or budget safety triggers.
- Dynamic generation of entire new resume formats (styling/structure) rather than tailoring content fields.
