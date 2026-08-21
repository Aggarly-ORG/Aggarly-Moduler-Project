-- =============================================================================
-- V28: Add name column to conversations and ai_conversations tables
-- =============================================================================

-- Add name column to conversations table and backfill from title
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS name VARCHAR(255);
UPDATE conversations SET name = title WHERE name IS NULL AND title IS NOT NULL;

-- Add name column to ai_conversations table and backfill from title
ALTER TABLE ai_conversations ADD COLUMN IF NOT EXISTS name VARCHAR(255);
UPDATE ai_conversations SET name = title WHERE name IS NULL AND title IS NOT NULL;
