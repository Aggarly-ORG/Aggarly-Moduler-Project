-- V40: Admin Telemetry, Sanctuaries Moderation, Payment Radar & User KYC Schema

-- 1. Properties additions for sensory, astronomical diagnostics and moderation
ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS bortle_class INT DEFAULT 3,
    ADD COLUMN IF NOT EXISTS acoustic_ambient_db NUMERIC(4, 1) DEFAULT 25.0,
    ADD COLUMN IF NOT EXISTS astrophotography_score NUMERIC(3, 2) DEFAULT 0.95,
    ADD COLUMN IF NOT EXISTS featured_spotlight BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS revision_notes TEXT;

CREATE INDEX IF NOT EXISTS idx_properties_status ON properties(status);
CREATE INDEX IF NOT EXISTS idx_properties_featured ON properties(featured_spotlight) WHERE featured_spotlight = true;

-- 2. Payment Attempts additions for Stripe Radar fraud telemetry
ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS radar_risk_score INT,
    ADD COLUMN IF NOT EXISTS radar_risk_level VARCHAR(50),
    ADD COLUMN IF NOT EXISTS three_d_secure_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ip_origin_country VARCHAR(10);

-- 3. AI Tool Invocations additions for agent mesh and latency breakdown
ALTER TABLE ai_tool_invocations
    ADD COLUMN IF NOT EXISTS agent_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS initiator VARCHAR(50),
    ADD COLUMN IF NOT EXISTS inference_duration_ms BIGINT,
    ADD COLUMN IF NOT EXISTS tool_execution_duration_ms BIGINT;

CREATE INDEX IF NOT EXISTS idx_ai_tool_invocations_agent ON ai_tool_invocations(agent_name);

-- 4. Users additions for moderation, status, and KYC verification tier
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(32) DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS kyc_tier VARCHAR(16) DEFAULT 'TIER_I',
    ADD COLUMN IF NOT EXISTS kyc_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trust_score INT DEFAULT 85;

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
