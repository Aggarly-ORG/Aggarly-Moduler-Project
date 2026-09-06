-- Migration: V37__update_refresh_token_family_and_revocation.sql
-- Description: Add family_id, revoked_at, and replaced_by_token to refresh_tokens with performance indexes

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS family_id UUID,
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS replaced_by_token VARCHAR(255);

-- Backfill existing tokens to have their own unique family_id
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

-- Make family_id NOT NULL
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

-- Performance and query indexes
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_cleanup ON refresh_tokens(revoked, expiry_date, revoked_at);
