-- Add ai_conversation_id column to conversations table for AI Concierge threads
ALTER TABLE conversations ADD COLUMN ai_conversation_id UUID;
CREATE INDEX idx_conversations_ai_conversation_id ON conversations(ai_conversation_id);
