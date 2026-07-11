-- V3: Add profile_id column to application table for linking applications to profiles.
-- The application table already has job_id (added in V1).
ALTER TABLE application ADD COLUMN IF NOT EXISTS profile_id UUID;
