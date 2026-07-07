# API Sketch

This is a first-pass interface sketch for implementation planning.

## Applications

`GET /api/applications`

Returns the application feed with job, match score, status, submission time, and skip/failure reason.

`GET /api/applications/{id}`

Returns the full audit trail, generated documents, groundedness report, and automation screenshots.

## Jobs

`POST /api/sources/greenhouse/sync`

Starts a Greenhouse sync for configured public boards.

`GET /api/jobs`

Returns normalized and deduped jobs.

## Policy

`GET /api/policy`

Returns autonomy threshold, daily caps, blocklist, and platform breaker states.

`PUT /api/policy`

Updates allowed policy settings.

`POST /api/automation/pause`

Activates the kill switch.

`POST /api/automation/resume`

Resumes automation after explicit re-enable.

## Events

`GET /api/applications/{id}/events`

Returns immutable automation and lifecycle events.
