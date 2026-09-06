-- V36__add_origin_context_to_scheduled_tasks.sql
-- Adds origin_context JSONB column to persist originating conversation context, channel, and message reference.

ALTER TABLE scheduled_task ADD COLUMN IF NOT EXISTS origin_context JSONB;

COMMENT ON COLUMN scheduled_task.origin_context IS 'Stores originating conversation context, channel, and message metadata that initiated the scheduled task';
