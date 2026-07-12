# Data Model

## MasterProfile

Stores user-level constraints and automation policy.

- `id`
- `user_id`
- `work_authorization`
- `visa_sponsorship_needed`
- `salary_floor`
- `locations`
- `remote_preference`
- `blocklist_companies`
- `daily_application_cap`
- `autonomy_threshold`

## ProfileFact

The source of truth for generated content.

- `id`
- `master_profile_id`
- `type`
- `employer`
- `title`
- `start_date`
- `end_date`
- `bullet_text`
- `skills`

## Job

- `id`
- `source`
- `external_id`
- `url`
- `company`
- `title`
- `location_type`
- `description_raw`
- `description_clean`
- `skills_required`
- `seniority`
- `salary_range`
- `work_auth_required`
- `sponsorship_available`
- `posted_at`
- `scraped_at`
- `last_verified_at`
- `status`
- `dedup_key`
- `embedding_vector`

## Application

- `id`
- `job_id`
- `resume_version_id`
- `cover_letter_version_id`
- `match_score`
- `match_score_breakdown`
- `automation_tier`
- `status`
- `submitted_at`
- `created_at`
- `platform`
- `external_application_id`
- `groundedness_check_passed`
- `groundedness_report`

## GeneratedDocument

- `id`
- `application_id`
- `type`
- `content`
- `generated_at`
- `source_facts_used`

## AutomationEvent

Immutable audit event.

- `id`
- `application_id`
- `event_type`
- `payload`
- `created_at`

## CircuitBreakerState

- `id`
- `scope`
- `status`
- `tripped_at`
- `reason`
- `error_count_window`
