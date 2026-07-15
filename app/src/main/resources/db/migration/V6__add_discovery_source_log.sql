-- Track discovery runs per source for the dashboard
CREATE TABLE IF NOT EXISTS discovery_run_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source      TEXT NOT NULL,
    ran_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    jobs_fetched INTEGER NOT NULL DEFAULT 0,
    jobs_new     INTEGER NOT NULL DEFAULT 0
);

-- Index for dashboard queries
CREATE INDEX IF NOT EXISTS idx_discovery_run_log_source ON discovery_run_log(source);
CREATE INDEX IF NOT EXISTS idx_discovery_run_log_ran_at ON discovery_run_log(ran_at DESC);
