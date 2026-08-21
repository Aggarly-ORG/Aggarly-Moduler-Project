-- Ensure the system AI Concierge user exists for chat conversations
INSERT INTO users (
    id, email, first_name, last_name, display_name, username,
    email_verified, auth_provider, is_deleted, created_at, updated_at
) VALUES (
    'aaac7011-3626-460c-a47e-c94535d34c65',
    'ai-concierge@aggarly.internal',
    'Aggarly',
    'AI Concierge',
    'Aggarly AI',
    'ai_concierge',
    TRUE,
    'SYSTEM',
    FALSE,
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;
