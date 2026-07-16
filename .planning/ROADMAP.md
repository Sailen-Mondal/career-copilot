# Roadmap

### Phase 1: LLM Integrations & Cost Optimization
**Goal:** Implement primary/secondary/fallback LLM chains and cost-control gates across job discovery, matching, automation, and resume tailoring.
**Success Criteria:**
1. LLM client supports fallback routing and primary/secondary chains.
2. Job matching utilizes cheap embeddings, calling semantic LLM matching only on borderline scores (60-85).
3. Custom form questions resolve via answer bank, with LLM generation only when missing, and cache results.
4. Resume tailoring uses primary generation and secondary groundedness checks, falling back to static resume on failure.

#### Requirements Mapped:
- `LLM-INGEST-01`
- `LLM-MATCH-01`
- `LLM-AUTO-01`
- `LLM-TAILOR-01`
- `LLM-COST-01`
- `LLM-CHAIN-01`
