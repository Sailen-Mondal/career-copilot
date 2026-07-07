# AI Career Copilot — Revised Architecture & Delivery Plan (v2)

*A structural review of the original roadmap — what breaks, why, and how to fix it — with the submission engine redesigned for full automation and no human approval gate.*

---

## 1. The Automation Decision (read this first)

Your instruction: remove *"requires your approval before submission"* → fully automated. This is implemented throughout this plan. But it's the single highest-leverage change in the whole system, so it gets addressed head-on before anything else, once, plainly — not as a recurring disclaimer.

**What you're removing.** The original Phase 5 had one hard rule: *never auto-submit, submission requires explicit confirmation.* That rule wasn't just about submission — it was the circuit breaker for every other failure mode upstream: a bad match score, a hallucinated resume line, a broken form-fill, a wrong file upload. Removing it doesn't remove those failure modes. It removes the thing that was catching them before they reached an employer.

**What's actually at stake, concretely:**

- **Platform enforcement is real and active, not theoretical.** LinkedIn's User Agreement explicitly prohibits bots, scrapers, and any software that automates activity on the platform, and enforcement is behavioral — velocity, browser fingerprinting, session-consistency checks — not a simple keyword match. LinkedIn's own March 2026 transparency reporting cited tens of millions of automated sessions flagged in a single quarter, so this isn't a rarely-enforced clause. Consequences are contractual (account restriction or permanent ban), not criminal — but a banned LinkedIn account means losing your network, message history, and recruiter relationships, which for a job search is a real cost.
- **The employer side is already fighting this exact pattern.** Named "apply bots" (LoopCV, LazyApply, Jobsolv, and similar) already blast large volumes of auto-generated applications, and it's created what Greenhouse's own CEO has publicly called an "AI doom loop" — so many indistinguishable AI-written applications that recruiters can't process them. By mid-2026, a large majority of employers reportedly run filters specifically tuned to flag applications that look AI-generated or bot-submitted, and the specific tells they look for include things like an application landing seconds after a job posts, or a cover letter that mirrors the job description's exact phrasing. That matters directly for this design — it means "fast" and "close keyword match" are exactly the signals that get an application auto-filtered before a human ever sees it.
- **No undo.** Very few ATS platforms let you withdraw a submitted application. Whatever goes out is what the employer sees, permanently.
- **Legally sensitive fields show up constantly.** Work authorization, sponsorship, salary expectations, EEO/self-identification questions. Getting one wrong isn't a UI bug, it's a real (if usually low-severity) misstatement submitted under your name, and it can't be quietly retracted.
- **Failures fail at scale, silently.** A broken selector, a template bug, or a miscalibrated match threshold doesn't fail once — it fails identically on every subsequent run, because nobody's watching each one anymore.

**The engineering answer.** Since a human isn't reviewing each submission, the design has to replace human judgment with automated judgment everywhere it can, and shrink the blast radius everywhere it can't. That thread runs through this entire plan:

- **Groundedness verification** (§6) — every generated line is programmatically checked against your master profile before anything is allowed out. A claim that can't be traced to source blocks that submission automatically — not "flagged for review," blocked outright, with an alert.
- **Tiered autonomy** (§7) — auto-submit only above a confidence threshold you set; anything with a custom essay question or an unrecognized field is skipped and logged, never guessed at.
- **Rate limits + circuit breakers** (§7) — hard per-day/per-platform caps, independent of match score, plus an automatic pause if error rate or CAPTCHA-block rate spikes.
- **Immutable audit log** (§7) — every submission (job, resume version, score, timestamp, platform response) recorded, so you can see exactly what went out even without approving it beforehand.
- **A kill switch** (§7) — one control, always reachable, that halts all future runs. This is the one "human in the loop" element that survives: not per-submission approval, but an always-available full stop.
- **Staged rollout** — shadow mode (fills forms, screenshots them, never clicks submit) before low-volume live, before scale.

You're getting full automation as asked. But "fully automated" and "unattended and unchecked" don't have to be the same thing, and the rest of this plan treats them as different.

---

## 2. Scope Reality Check

Before the phase-by-phase fixes: the original roadmap is architecturally reasonable but scoped like a funded team's 6–12 month build, not a solo portfolio project. Phase 0 gets a week; phases 1–8 get no estimate at all. Seven "services," two automation engines, a full analytics dashboard, and a production deployment is a lot of surface area for one person — especially with a browser-automation half that needs constant upkeep as sites change their HTML.

**Fix — build a walking skeleton first, expand later.** A realistic MVP cut (~4–6 weeks, part-time, solo):

- One job source: Greenhouse's public job board API (documented JSON, built for exactly this, no scraping, no login)
- Matching: embeddings + keyword overlap against one resume
- Generation: one resume/cover-letter template, with the groundedness check in place from day one (cheap to build early, expensive to retrofit)
- Automation: Playwright for one platform's apply flow, shadow mode only, until reliable
- One Spring Boot modular monolith (not seven services)
- A bare dashboard — a table of applications is enough at first

Everything else (LinkedIn/Indeed as sources, service extraction, full analytics, multi-platform scale) becomes Phase 2+ expansion once the skeleton proves out. This also derisks the automation question on its own: you get real signal on match quality and generation quality on a small, safe surface before trusting it to submit anything unattended.

---

## 3. Architecture: Monolith First, Not Seven Services

**Problem.** Splitting into `auth-service, job-service, resume-service, ai-service, browser-service, application-service, notification-service` from day one means seven deployables, seven configs/logs/health checks, inter-service auth, and network calls — for what is, functionally, a single-user tool. This is the largest source of accidental complexity in the original plan and will slow a solo build down more than anything else on this list.

**Fix — modular monolith**, one Spring Boot application, one deployable, hard package boundaries:

```
com.careercopilot
├── auth/
├── profile/          (master resume, structured verified facts)
├── discovery/        (crawler, normalizer, dedup)
├── matching/         (scoring, embeddings, eligibility filter)
├── generation/       (resume/cover letter tailoring + groundedness check)
├── automation/       (Playwright orchestration, autonomy policy engine)
├── applications/     (application lifecycle, audit log)
├── notification/
└── shared/           (common DTOs, domain events)
```

Packages talk to each other only through interfaces + DTOs, publishing domain events (`JobDiscovered`, `MatchScored`, `ResumeGenerated`, `ApplicationSubmitted`) on an in-process event bus (`ApplicationEventPublisher`, or Redis Streams if you want it durable). That's the seam you'd cut along if you ever extract a real service.

**One real exception:** `automation` is worth deploying separately if you can, from day one — different runtime footprint (headless/headful Chromium, much higher memory), different failure profile (site changes, CAPTCHAs, timeouts) that shouldn't cascade into the rest of the app, and it's the one component genuinely likely to need independent scaling later. So: monolith for everything, with `automation` optionally split out as its own process talking over a queue rather than direct calls. One seam, not seven.

**Tech stack, updated:**

| Layer | Original plan | Recommendation | Why |
|---|---|---|---|
| Language | Java 21 | Java 21 LTS (safe) or Java 25 LTS (current LTS, Sept 2025) | Spring Boot 4.x needs Java 17 minimum; either LTS is fine, 25 is more current |
| Framework | "Spring Boot" (unversioned) | Spring Boot 4.1 / Spring Framework 7 | Spring Boot 3.5 reached end-of-life June 30, 2026 — don't start a new project on a dying line |
| DB | PostgreSQL | PostgreSQL + `pgvector` | Keeps embeddings in one database instead of standing up a separate vector store |
| Migrations | (unspecified) | Flyway, from commit #1 | Retrofit-cost avoidance |
| Browser automation | Playwright | Playwright (current 1.6x line), Java binding | Official Java client exists; no need to shell out to Node |
| Messaging | RabbitMQ ("later") | Redis Streams to start, RabbitMQ if you outgrow it | One less moving part for a solo build |
| Secrets | (unspecified) | Encrypted config (Jasypt) minimum; Vault/cloud KMS if this ever leaves "personal tool" | See §8 |

---

## 4. Phase-by-Phase Problems & Fixes

### Phase 0 — Research & Design

| Problem | Why it matters | Fix |
|---|---|---|
| No source-legality review | LinkedIn/Indeed forbid automated access in their terms; deciding this after building around it is expensive | Add a **Source Legality Matrix**: for each source, mark public API / ToS-permitted / authenticated-scraping (avoid) |
| No non-functional requirements | "Fast" and "reliable" aren't requirements | Write explicit NFRs: crawl latency, max daily applications, uptime target, data retention window |
| No success metrics defined | Can't tell if the tool is actually working | Track interview rate per application, time saved per application, false-positive rate on matching |

### Phase 1 — Core Backend

| Problem | Fix |
|---|---|
| Seven microservices for a solo build | Modular monolith (§3) |
| No secrets management named at all | Encrypted credential storage from day one — never plaintext in the DB (§8) |
| No migration tooling | Flyway |
| No test strategy | JUnit5 + Mockito for units, Testcontainers (real Postgres/Redis in Docker) for integration |
| CI/CD deferred to Phase 8 | Stand up build+test+lint automation in Phase 1 |

### Phase 2 — Job Discovery

| Problem | Fix |
|---|---|
| LinkedIn/Indeed listed as scrape targets | Both explicitly restrict this in their terms and detect it behaviorally. Use Greenhouse/Lever's public job board APIs (built for exactly this use); treat LinkedIn/Indeed as manual-import sources instead of autonomous scraping targets |
| No rate limiting / robots.txt handling specified | Token-bucket limiter per domain, exponential backoff, honor robots.txt |
| Dedup is a one-liner | Composite key (normalized company + title + location) plus embedding similarity for cross-posted listings |
| No staleness handling | Periodic liveness check; mark `EXPIRED` on 404 or "no longer accepting applications" |
| No skills normalization | Canonical skills taxonomy + embedding match so "Spring Boot" ≈ "Spring" ≈ "spring-boot" |

### Phase 3 — AI Matching

| Problem | Fix |
|---|---|
| Keyword overlap only | Weighted, semantic (embedding) similarity; separate must-have vs. nice-to-have weighting parsed from the JD |
| No eligibility pre-filter | Filter work authorization / seniority / location **before** scoring — don't burn generation cost or application slots on jobs you're ineligible for |
| No company blocklist | Current employer, named competitors, previously-rejected-within-N-months excluded automatically |
| No outcome feedback loop | Log interview/rejection/silence per application; periodically recalibrate weighting |
| Untrusted input surface | Scraped job descriptions are fed straight into prompts — see prompt-injection note in §6 |

### Phase 4 — Resume & Cover Letter Tailoring

| Problem | Fix |
|---|---|
| "No hallucinations" is a principle with no enforcement mechanism | Structured master profile + a programmatic groundedness verifier, not just a prompt instruction (§6) |
| Cover letters named in the original goal, absent from this phase | Same grounding pipeline applies explicitly to cover letters |
| No versioning/traceability | Every generated document is a version tied to a specific job + an audit record |
| No PII protection called out | Master profile encrypted at rest — it's exactly the data a DB breach makes painful |
| Risk of sounding "too AI" | Employers are actively filtering for generic AI phrasing and JD-mirroring language (§1) — tailoring should reword and select, not template-stuff keywords |

### Phase 5 — Browser Automation

| Problem | Fix |
|---|---|
| No CAPTCHA / bot-detection story | Some platforms will simply block headless automation. Design for graceful failure — log, skip, alert — rather than trying to defeat detection |
| No MFA handling | Session/token refresh with a flagged fallback when MFA interrupts a flow, instead of assuming unattended login always works |
| Brittle selectors | Prefer `data-testid`/ARIA-role selectors with fallback chains; scheduled canary runs against known postings to catch breakage before it hits a real application |
| Custom essay questions | Highest hallucination-risk surface. Keep a small, pre-approved answer bank for common ones ("why this company"); skip anything outside that bank rather than freeform-generate |
| The removed approval gate | Replaced by the tiered autonomy engine (§7) |

### Phase 6 — AI Workflow Engine

| Problem | Fix |
|---|---|
| No idempotency/retry story | Each stage is checkpointed; a crash mid-flow resumes instead of restarting or double-submitting |
| No circuit breaker | Auto-pause a platform/source if its error rate spikes (layout change, expired credentials) |
| No daily cap | Hard per-day/per-platform ceiling, independent of confidence score — protects against a scoring bug going wide |

### Phase 7 — Dashboard

| Problem | Fix |
|---|---|
| No kill switch | One-click "pause everything," visible on every screen |
| No post-hoc review surface | A feed of everything submitted today, so mistakes get caught fast — just after the fact instead of before |
| No autonomy tuning UI | Threshold/policy controls for §7's tiered autonomy, not a code change |

### Phase 8 — Deployment

| Problem | Fix |
|---|---|
| No secrets story in production | Docker secrets / cloud KMS — never baked into images |
| No backup/DR plan | Scheduled Postgres backups — this database holds your entire application history |
| No monitoring | Minimum viable: structured logs + one alert channel for automation failures and circuit-breaker trips |

---

## 5. Data Model Additions

The original schema covers `Job` reasonably but has **no `Application` entity at all** — a real gap, since applications are the thing this system exists to produce.

```
MasterProfile
 - id, user_id
 - work_authorization (enum — structured, not free text)
 - visa_sponsorship_needed (bool)
 - salary_floor, locations[], remote_pref
 - blocklist_companies[]
 - daily_application_cap
 - autonomy_threshold (0–100, minimum match score for auto-submit)

ProfileFact                  (source of truth the groundedness checker verifies against)
 - id, master_profile_id
 - type (experience | education | certification | skill | project)
 - employer, title, start_date, end_date, bullet_text, skills[]

Job
 - id, source, external_id, url
 - company, title, location_type, description_raw, description_clean
 - skills_required[], seniority, salary_range
 - work_auth_required, sponsorship_available
 - posted_at, scraped_at, last_verified_at, status (active|expired|removed)
 - dedup_key, embedding_vector

Application
 - id, job_id, resume_version_id, cover_letter_version_id
 - match_score, match_score_breakdown (json)
 - automation_tier (auto | skipped_low_confidence | skipped_unsupported_field)
 - status (queued|generating|verifying|ready|submitted|blocked|failed|withdrawn|interview|rejected|offer)
 - submitted_at, platform, external_application_id
 - groundedness_check_passed (bool), groundedness_report (json)
 - audit_trail (json — every step, timestamped)

GeneratedDocument
 - id, application_id, type (resume|cover_letter), content, generated_at
 - source_facts_used[]     (FK list into ProfileFact — what the verifier checks against)

AutomationEvent             (the audit log)
 - id, application_id, event_type, payload, created_at

CircuitBreakerState
 - id, scope (platform name), status (closed|open), tripped_at, reason, error_count_window
```

`pgvector` on the same Postgres instance keeps embeddings in one database rather than adding a separate vector store — proportionate for this scale.

---

## 6. AI Pipeline Safety Design

### Groundedness verification (the automated stand-in for human review)

1. `MasterProfile` + `ProfileFact` is the only source of truth. The generation prompt is explicitly restricted to *selecting and rewording* existing facts for the target JD — never inventing new ones.
2. After generation, an extraction pass (a second, cheap model call or a rule-based parser) pulls every factual claim out of the generated text — employers, dates, numbers, skills — and diffs it against the `ProfileFact` rows actually used.
3. Anything unmatched fails the check. On failure: don't submit, log it, alert, optionally fall back to a previously-verified template rather than skip the job outright.
4. This check is **mandatory and blocking**, not advisory — it's the piece doing the job a human reviewer used to do.

### Prompt injection from scraped content

A job description is untrusted the moment it's scraped. A posting could contain adversarial text aimed at your matching or generation prompts — the mirror image of the well-known trick of hiding invisible text for ATS bots, except here the job posting is the untrusted side aimed at *your* pipeline. Mitigations:

- Strip HTML and any invisible/off-screen text before it reaches a prompt
- Keep untrusted content in a clearly delimited field, separate from system instructions, with an explicit instruction not to treat that field as commands
- Sanity-check outputs — if a match score or generated document looks anomalous relative to the actual JD text, flag it rather than trust it

### Matching improvements

- Weighted must-have vs. nice-to-have parsing from the JD, not flat keyword overlap
- Embedding similarity to catch synonyms ("K8s" ≈ "container orchestration")
- Hard eligibility pre-filter (work auth, seniority, location) before scoring
- Outcome feedback loop: log interview/rejection signal per application, periodically recalibrate

---

## 7. The Submission Engine — Fully Automated, Engineered Guardrails

This is the direct, concrete replacement for the approval step you removed.

### Tiered autonomy policy (automatic, not a human gate)

| Tier | Condition | Behavior |
|---|---|---|
| **Auto-submit** | Match score ≥ your threshold, groundedness check passed, no unsupported custom fields | Submitted automatically, logged |
| **Skip + log** | Score below threshold, OR groundedness check failed, OR a custom question outside the pre-approved answer bank | Not submitted, not guessed at — recorded with a reason, visible on the dashboard |
| **Blocked** | Company on blocklist, missing eligibility, expired posting | Never enters the pipeline |

Every application either goes out clean or gets skipped with a reason. There's no third path where something questionable ships anyway.

### Rate limiting & circuit breakers

- Hard daily cap per platform, independent of match score — protection against a scoring bug going wide
- Randomized pacing between actions — this is about not hammering a site's infrastructure, not about defeating detection
- Per-platform circuit breaker: failure rate or CAPTCHA-block rate crossing a threshold in a rolling window auto-pauses that platform and alerts you; requires manual re-enable

### Audit trail

Every submission logs: job, resume version, match score + breakdown, a screenshot at submission time if feasible, timestamp, platform response. This is what lets you review after the fact even though nothing was reviewed before the fact.

### Kill switch

One control — dashboard button, CLI command, and health-check-triggered auto-halt — that stops all future runs immediately. This is the one human-in-the-loop element that survives: not per-submission approval, but an always-available full stop.

### Staged rollout

1. **Shadow mode** — full pipeline runs, browser fills the form, screenshots it, never clicks submit
2. **Low-volume live** — one platform, a low daily cap, 1–2 weeks, reviewing the audit log daily
3. **Scale up** — raise caps and add platforms once false-positive and failure rates are known-good

---

## 8. Security & Compliance Checklist

- **Credentials** — never store platform passwords in plaintext; encrypted at rest at minimum, a vault (HashiCorp Vault / cloud KMS) with short-lived session tokens if you go further
- **PII** — master profile data encrypted at rest, access-logged
- **Dependency hygiene** — OWASP dependency-check or Snyk in CI
- **Input validation** — sanitize all scraped HTML before storage or prompting
- **Platform terms** — not legal advice, but worth being explicit: most major job platforms restrict automated access and bulk applications in their terms; the foreseeable consequence is account action (restriction/ban), not typically legal liability — but it's worth planning for "what happens to my search if this account gets suspended mid-search"
- **Cost control** — LLM calls for matching and generation add up at volume; cache embeddings, batch where possible, and set a monthly budget alert
- **Data retention** — decide upfront how long scraped job data and rejected-application records are kept; you don't need to keep everything forever

---

## 9. Testing Strategy

| Layer | Tool | What it catches |
|---|---|---|
| Unit | JUnit5 + Mockito | Business logic — matching math, autonomy-tier decisions |
| Integration | Testcontainers (real Postgres/Redis) | Schema and query bugs that mocks hide |
| Contract | WireMock | Greenhouse/Lever API or LLM API changes breaking your integration |
| Browser E2E | Playwright test runner, scheduled | Selector breakage caught by a canary run before it hits a real application |
| AI eval | Custom harness | Groundedness pass rate, match-score calibration against labeled examples |

---

## 10. Revised Milestones

| Milestone | Outcome | Realistic estimate (solo, part-time) |
|---|---|---|
| M0 | Source legality matrix, NFRs, architecture doc | 3–5 days |
| M1 | Modular monolith skeleton, auth, DB, CI | 1–2 weeks |
| M2 | Greenhouse/Lever ingestion + dedup | ~1 week |
| M3 | Matching engine v1 (embeddings + eligibility filter) | 1–2 weeks |
| M4 | Generation + groundedness verifier | 2–3 weeks |
| M5 | Browser automation, shadow mode only | 2–3 weeks |
| M6 | Tiered autonomy + rate limits + circuit breakers | 1–2 weeks |
| M7 | Dashboard: feed, kill switch, autonomy controls | 1–2 weeks |
| M8 | Low-volume live rollout, one platform | 1–2 weeks observation |
| M9 | Scale: more sources, more volume | ongoing |
| M10 | Production hardening, backups, monitoring | ~1 week |

Roughly 4–5 months part-time to a genuinely trustworthy v1 — not eight undated phases. That's the difference between a plan and a plan you'll actually finish.

---

## 11. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Platform bans your account | Medium–High | High | Public APIs where possible, rate limits, per-platform circuit breaker |
| Hallucinated content submitted (no gate) | Medium | High | Mandatory blocking groundedness verifier |
| Selector breakage silently fails submissions | High | Medium | Scheduled canary tests, alerting on failure spikes |
| Matching bug causes a burst of irrelevant applications | Low–Medium | High | Hard daily cap independent of score, circuit breaker |
| Prompt injection via scraped job description | Low–Medium | Medium | Untrusted-content delimiting, output sanity checks |
| Legally sensitive field answered wrong | Medium | Medium–High | Pre-approved fixed answers for known sensitive fields; skip unknowns |
| Credential leak (DB breach) | Low | High | Encryption at rest, vault for secrets, never log credentials |
| Scope creep kills the project before v1 ships | High | High | Walking-skeleton MVP (§2), phased expansion |

---

## 12. Repository Structure (Revised)

```
career-copilot/
├── app/                        # the modular monolith
│   ├── src/main/java/com/careercopilot/
│   │   ├── auth/  profile/  discovery/  matching/
│   │   ├── generation/  applications/  notification/  shared/
│   └── src/test/...
├── automation/                 # separately-deployable Playwright service
├── frontend/                   # dashboard
├── docs/
│   ├── SRS.md  Architecture.md  DataModel.md  API.md
│   ├── SourceLegalityMatrix.md
│   └── RiskRegister.md
└── infra/
    ├── docker-compose.yml
    └── ci/
```

---

## Closing Note

Your original vision is intact — multi-source discovery, AI matching, tailored generation, browser automation, full automation on submission. What's changed is that the one safety mechanism you removed is replaced by several automated ones that don't need you watching: a blocking groundedness check instead of a human sanity-read, confidence-tiered auto-submit instead of a review queue, rate limits and circuit breakers instead of a person noticing something's gone wrong, and a kill switch as the one control that's still always yours. The submission step was always the riskiest part of this system. It still is — it's just guarded by code now instead of by you.
