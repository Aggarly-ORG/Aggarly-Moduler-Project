-- ============================================================================
-- V30: Create User Payment Methods and Confirmed Actions Tables (Database-Backed)
-- ============================================================================

-- 1. User Saved Payment Methods Table
CREATE TABLE IF NOT EXISTS user_payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stripe_payment_method_id VARCHAR(255) NOT NULL,
    card_brand VARCHAR(50) NOT NULL,
    last_four VARCHAR(4) NOT NULL,
    exp_month INT NOT NULL,
    exp_year INT NOT NULL,
    cardholder_name VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_payment_methods_user_id 
ON user_payment_methods (user_id);

-- 2. User Confirmed Actions Table (Persistent across all devices and sessions)
CREATE TABLE IF NOT EXISTS user_confirmed_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID,
    confirmation_token VARCHAR(255) NOT NULL UNIQUE,
    tool_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    details_json TEXT,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_confirmed_actions_user 
ON user_confirmed_actions (user_id);

CREATE INDEX IF NOT EXISTS idx_user_confirmed_actions_token 
ON user_confirmed_actions (confirmation_token);
