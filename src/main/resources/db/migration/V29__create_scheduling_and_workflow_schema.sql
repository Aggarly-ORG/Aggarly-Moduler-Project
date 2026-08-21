-- ============================================================================
-- V29: Aggarly AI Scheduling & Workflow Engine Tables
-- ============================================================================

-- 1. Scheduled Tasks Table
CREATE TABLE IF NOT EXISTS scheduled_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    trigger_type VARCHAR(32) NOT NULL,
    execution_type VARCHAR(32) NOT NULL DEFAULT 'DETERMINISTIC',
    misfire_policy VARCHAR(32) NOT NULL DEFAULT 'RUN_ONCE_NOW',

    -- Schedule Timing
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    next_execution_at TIMESTAMP WITH TIME ZONE,
    last_execution_at TIMESTAMP WITH TIME ZONE,
    last_started_at TIMESTAMP WITH TIME ZONE,
    last_finished_at TIMESTAMP WITH TIME ZONE,

    -- Retry & Failure Tracking
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT,

    -- JSON Configurations
    trigger_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    plan JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- Versioning & Auditing
    plan_version INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for scheduler polling (Targeting due active tasks with SKIP LOCKED)
CREATE INDEX IF NOT EXISTS idx_scheduled_task_due 
ON scheduled_task (next_execution_at) 
WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_scheduled_task_user_status 
ON scheduled_task (user_id, status);

-- 2. Event Trigger Mapping Table
CREATE TABLE IF NOT EXISTS scheduled_event_trigger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES scheduled_task(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    filter_config JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_trigger_lookup 
ON scheduled_event_trigger (event_type);

-- 3. Task Execution History & Attempts Table
CREATE TABLE IF NOT EXISTS scheduled_task_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES scheduled_task(id) ON DELETE CASCADE,
    plan_version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL DEFAULT 1,

    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,

    error_code VARCHAR(64),
    error_message TEXT,
    step_results JSONB DEFAULT '{}'::jsonb,

    correlation_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_execution_history 
ON scheduled_task_execution (task_id, started_at DESC);

-- 4. Transactional Outbox Table for Guaranteed Event Delivery
CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending 
ON outbox_event (created_at) 
WHERE status = 'PENDING';
