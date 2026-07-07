CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE master_profile (
    id UUID PRIMARY KEY,
    user_id TEXT NOT NULL,
    work_authorization TEXT NOT NULL,
    visa_sponsorship_needed BOOLEAN NOT NULL,
    salary_floor INTEGER,
    locations JSONB NOT NULL DEFAULT '[]',
    remote_preference TEXT,
    blocklist_companies JSONB NOT NULL DEFAULT '[]',
    daily_application_cap INTEGER NOT NULL,
    autonomy_threshold INTEGER NOT NULL CHECK (autonomy_threshold BETWEEN 0 AND 100)
);

CREATE TABLE profile_fact (
    id UUID PRIMARY KEY,
    master_profile_id UUID NOT NULL REFERENCES master_profile(id),
    type TEXT NOT NULL,
    employer TEXT,
    title TEXT,
    start_date DATE,
    end_date DATE,
    bullet_text TEXT NOT NULL,
    skills JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE job (
    id UUID PRIMARY KEY,
    source TEXT NOT NULL,
    external_id TEXT,
    url TEXT NOT NULL,
    company TEXT NOT NULL,
    title TEXT NOT NULL,
    location_type TEXT,
    description_raw TEXT,
    description_clean TEXT,
    skills_required JSONB NOT NULL DEFAULT '[]',
    seniority TEXT,
    salary_range JSONB,
    work_auth_required TEXT,
    sponsorship_available BOOLEAN,
    posted_at TIMESTAMPTZ,
    scraped_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_verified_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'active',
    dedup_key TEXT,
    embedding_vector VECTOR(1536)
);

CREATE TABLE generated_document (
    id UUID PRIMARY KEY,
    application_id UUID,
    type TEXT NOT NULL,
    content TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_fact_ids JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE application (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES job(id),
    resume_version_id UUID,
    cover_letter_version_id UUID,
    match_score INTEGER NOT NULL CHECK (match_score BETWEEN 0 AND 100),
    match_score_breakdown JSONB NOT NULL DEFAULT '{}',
    automation_tier TEXT NOT NULL,
    status TEXT NOT NULL,
    submitted_at TIMESTAMPTZ,
    platform TEXT,
    external_application_id TEXT,
    groundedness_check_passed BOOLEAN NOT NULL DEFAULT false,
    groundedness_report JSONB NOT NULL DEFAULT '{}',
    audit_trail JSONB NOT NULL DEFAULT '[]'
);

ALTER TABLE generated_document
    ADD CONSTRAINT fk_generated_document_application
    FOREIGN KEY (application_id) REFERENCES application(id);

ALTER TABLE application
    ADD CONSTRAINT fk_application_resume
    FOREIGN KEY (resume_version_id) REFERENCES generated_document(id);

ALTER TABLE application
    ADD CONSTRAINT fk_application_cover_letter
    FOREIGN KEY (cover_letter_version_id) REFERENCES generated_document(id);

CREATE TABLE automation_event (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE circuit_breaker_state (
    id UUID PRIMARY KEY,
    scope TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    tripped_at TIMESTAMPTZ,
    reason TEXT,
    error_count_window INTEGER NOT NULL DEFAULT 0
);
