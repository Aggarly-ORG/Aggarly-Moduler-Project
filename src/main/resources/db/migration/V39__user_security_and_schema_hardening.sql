-- ============================================================================
-- V39: User Security and Schema Hardening
-- ============================================================================

-- 1. Enforce unique payment method per user to prevent duplicate attachments
ALTER TABLE user_payment_methods
    ADD CONSTRAINT uq_user_payment_methods_user_stripe UNIQUE (user_id, stripe_payment_method_id);

-- 2. Add foreign key constraint from user_confirmed_actions to conversations
ALTER TABLE user_confirmed_actions
    ADD CONSTRAINT fk_user_confirmed_actions_conversation
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL;

-- 3. Drop redundant index on confirmation_token (already has unique constraint)
DROP INDEX IF EXISTS idx_user_confirmed_actions_token;

-- 4. Index for user directory listing and sorting by creation date
CREATE INDEX IF NOT EXISTS idx_users_created_at_desc ON users (created_at DESC);
