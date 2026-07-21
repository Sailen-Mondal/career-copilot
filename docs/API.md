# API Reference

This document provides complete endpoint details for the Career Copilot REST API.

## Authentication

All REST API requests require authentication via an HTTP request header.

```http
X-API-Key: cc_live_9f83a21b4e09712a83f1
```

Requests missing a valid API key will receive an `401 Unauthorized` response.

---

## Applications

Endpoints for managing job applications, audit trails, and execution events.

### `GET /api/applications`

Retrieves the application feed including match scores, statuses, submission timestamps, and failure/skip details.

**Example Response:**

```json
{
  "data": [
    {
      "id": "app_9b1deb4d-3b7d-4b69-9f1d-8a2e1c9d4001",
      "jobTitle": "Senior Backend Engineer",
      "company": "Stripe",
      "platform": "Greenhouse",
      "matchScore": 92,
      "status": "SUBMITTED",
      "appliedAt": "2026-07-21T14:30:00Z",
      "skipReason": null
    },
    {
      "id": "app_5a8c2f10-1e42-4f88-823c-911a7b3c2002",
      "jobTitle": "Full Stack Developer",
      "company": "Vercel",
      "platform": "Lever",
      "matchScore": 74,
      "status": "SKIPPED",
      "appliedAt": null,
      "skipReason": "Match score below autonomy threshold (80%)"
    }
  ],
  "total": 2,
  "page": 1,
  "pageSize": 20
}
```

### `GET /api/applications/{id}`

Retrieves complete application details, generated documents, groundedness verification report, and screenshot artifacts.

**Example Response:**

```json
{
  "id": "app_9b1deb4d-3b7d-4b69-9f1d-8a2e1c9d4001",
  "job": {
    "id": "job_108234",
    "title": "Senior Backend Engineer",
    "company": "Stripe",
    "location": "Remote (US)",
    "url": "https://boards.greenhouse.io/stripe/jobs/108234"
  },
  "matchScore": 92,
  "status": "SUBMITTED",
  "groundednessReport": {
    "verified": true,
    "score": 100,
    "unverifiedClaims": []
  },
  "documents": {
    "resumeUrl": "/api/documents/resumes/res_9b1deb4d.pdf",
    "coverLetterUrl": "/api/documents/cover-letters/cl_9b1deb4d.pdf"
  },
  "screenshots": [
    "/api/artifacts/screenshots/app_9b1deb4d_form.png",
    "/api/artifacts/screenshots/app_9b1deb4d_confirmation.png"
  ],
  "createdAt": "2026-07-21T14:28:10Z",
  "submittedAt": "2026-07-21T14:30:00Z"
}
```

### `GET /api/applications/{id}/events`

Retrieves the immutable event audit trail for a specific application.

**Example Response:**

```json
{
  "applicationId": "app_9b1deb4d-3b7d-4b69-9f1d-8a2e1c9d4001",
  "events": [
    {
      "timestamp": "2026-07-21T14:28:10Z",
      "eventType": "JOB_DISCOVERED",
      "details": "Job ingested from Greenhouse public board"
    },
    {
      "timestamp": "2026-07-21T14:28:45Z",
      "eventType": "GROUNDEDNESS_PASSED",
      "details": "Groundedness score: 100%. Zero unverified facts detected."
    },
    {
      "timestamp": "2026-07-21T14:29:15Z",
      "eventType": "QUEUED_FOR_AUTOMATION",
      "details": "Dispatched to Redis stream cc:automation:jobs"
    },
    {
      "timestamp": "2026-07-21T14:30:00Z",
      "eventType": "FORM_SUBMITTED",
      "details": "Playwright worker completed form submission successfully"
    }
  ]
}
```

---

## Jobs & Discovery

Endpoints for managing job listings and triggering source synchronization.

### `GET /api/jobs`

Retrieves normalized and deduplicated job postings.

**Example Response:**

```json
{
  "jobs": [
    {
      "id": "job_108234",
      "title": "Senior Backend Engineer",
      "company": "Stripe",
      "location": "Remote",
      "sourcePlatform": "Greenhouse",
      "salaryRange": "$180,000 - $220,000",
      "discoveredAt": "2026-07-21T12:00:00Z"
    }
  ],
  "total": 1
}
```

### `POST /api/sources/greenhouse/sync`

Triggers an on-demand synchronization job for configured Greenhouse job boards.

**Example Response:**

```json
{
  "status": "SYNC_STARTED",
  "source": "Greenhouse",
  "jobId": "sync_gh_881923",
  "startedAt": "2026-07-21T14:50:00Z"
}
```

---

## Profile

Endpoints for retrieving and updating user master career profiles and verified facts.

### `GET /api/profile`

Retrieves user profile data and verified claims.

**Example Response:**

```json
{
  "id": "usr_4021",
  "fullName": "Alex Mercer",
  "email": "alex.mercer@example.com",
  "phone": "+1-555-0199",
  "skills": ["Java", "Spring Boot", "TypeScript", "PostgreSQL", "Playwright"],
  "experience": [
    {
      "company": "Tech Corp",
      "role": "Staff Software Engineer",
      "startDate": "2022-01",
      "endDate": "Present",
      "highlights": ["Led architecture of modular monolith handling 10M daily events"]
    }
  ]
}
```

### `PUT /api/profile`

Updates the master career profile and verified fact repository.

**Example Response:**

```json
{
  "status": "UPDATED",
  "updatedAt": "2026-07-21T15:00:00Z",
  "verifiedFactCount": 18
}
```

---

## Policy & Automation

Endpoints for controlling application autonomy, safety limits, circuit breakers, and emergency controls.

### `GET /api/policy`

Retrieves current autonomy settings, daily submission caps, blocklists, and platform circuit breaker statuses.

**Example Response:**

```json
{
  "autonomyThreshold": 80,
  "dailyCap": 25,
  "applicationsToday": 12,
  "killSwitchActive": false,
  "blocklist": ["Company X", "Unverified Agency LLC"],
  "circuitBreakers": {
    "Greenhouse": "CLOSED",
    "Lever": "CLOSED",
    "Workday": "OPEN"
  }
}
```

### `PUT /api/policy`

Updates policy thresholds, limits, and platform rules.

**Example Response:**

```json
{
  "status": "POLICY_UPDATED",
  "autonomyThreshold": 85,
  "dailyCap": 30,
  "killSwitchActive": false
}
```

### `POST /api/automation/pause`

Activates the emergency kill switch, pausing all active and queued browser automation tasks instantly.

**Example Response:**

```json
{
  "status": "AUTOMATION_PAUSED",
  "killSwitchActive": true,
  "message": "All automation tasks paused immediately.",
  "timestamp": "2026-07-21T15:05:00Z"
}
```

### `POST /api/automation/resume`

Deactivates the emergency kill switch and resumes background automation processing.

**Example Response:**

```json
{
  "status": "AUTOMATION_RESUMED",
  "killSwitchActive": false,
  "message": "Automation worker processing resumed.",
  "timestamp": "2026-07-21T15:10:00Z"
}
```
