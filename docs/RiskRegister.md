# Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---:|---:|---|
| Platform account restriction | Medium-High | High | Prefer public APIs, low caps, circuit breakers |
| Hallucinated claim submitted | Medium | High | Blocking groundedness verifier |
| Selector breakage | High | Medium | Shadow mode, canaries, screenshots, alerts |
| Irrelevant application burst | Low-Medium | High | Daily caps independent of score |
| Prompt injection through job text | Low-Medium | Medium | Sanitize and delimit untrusted content |
| Sensitive field answered wrong | Medium | Medium-High | Fixed answer bank, skip unknowns |
| Credential leak | Low | High | Encrypt credentials, never log secrets |
| Scope creep | High | High | Walking skeleton, one source first |
