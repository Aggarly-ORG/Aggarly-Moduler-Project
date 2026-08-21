-- ============================================================================
-- V31: Add integrity constraints and ensure completed tasks have no next execution
-- ============================================================================

-- Ensure any completed or cancelled tasks have null next_execution_at
UPDATE scheduled_task
SET next_execution_at = NULL
WHERE status IN ('COMPLETED', 'CANCELLED');

-- Add check constraint ensuring completed/cancelled tasks cannot have a scheduled next run
ALTER TABLE scheduled_task
ADD CONSTRAINT chk_scheduled_task_completed_no_next_run
CHECK (status NOT IN ('COMPLETED', 'CANCELLED') OR next_execution_at IS NULL);
