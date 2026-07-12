-- V5: Add created_at timestamp to application table
ALTER TABLE application ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
