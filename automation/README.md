# Automation Worker

The automation worker is intentionally separate from the app core.

## First Target

Shadow mode only:

1. Receive a queued application command.
2. Open the target apply URL.
3. Fill known fields using deterministic mappings.
4. Skip on CAPTCHA, MFA, unsupported custom questions, or unknown sensitive fields.
5. Save screenshot and structured field report.
6. Never click submit in shadow mode.

## Live Mode Gate

Live mode should require all of these values from the app core:

- Match score is above threshold.
- Groundedness check passed.
- No unsupported custom fields were found.
- Daily cap is not exhausted.
- Platform circuit breaker is closed.
- Kill switch is not active.

## Event Contract

```json
{
  "applicationId": "uuid",
  "jobUrl": "https://example.com/apply",
  "mode": "shadow",
  "profileSnapshotId": "uuid",
  "resumeDocumentId": "uuid",
  "coverLetterDocumentId": "uuid"
}
```

## Output Contract

```json
{
  "applicationId": "uuid",
  "status": "shadow_completed",
  "fieldsFilled": ["first_name", "last_name", "email", "resume"],
  "unsupportedFields": [],
  "screenshotPath": "screenshots/application-id.png",
  "platformResponse": null
}
```
