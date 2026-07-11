-- V4: Add contact fields to master_profile table for form filling automation.
ALTER TABLE master_profile ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE master_profile ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE master_profile ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE master_profile ADD COLUMN IF NOT EXISTS linkedin_url TEXT;
ALTER TABLE master_profile ADD COLUMN IF NOT EXISTS website_url TEXT;
