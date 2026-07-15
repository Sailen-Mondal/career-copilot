-- Add retry and error logging columns to the application table for autonomous resilience
ALTER TABLE application ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE application ADD COLUMN IF NOT EXISTS last_error TEXT;
