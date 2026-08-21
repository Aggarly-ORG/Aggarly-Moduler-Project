-- =============================================================================
-- V24: Chat Module Schema
-- =============================================================================

-- Conversations Table
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    type VARCHAR(30) NOT NULL DEFAULT 'DIRECT', -- DIRECT, BOOKING_INQUIRY, SUPPORT, AI_CONCIERGE
    property_id UUID REFERENCES properties(id),
    booking_id UUID REFERENCES bookings(id),
    title VARCHAR(150),
    last_message_at TIMESTAMP NOT NULL,
    last_message_preview VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_conversations_type ON conversations(type);
CREATE INDEX idx_conversations_property ON conversations(property_id);
CREATE INDEX idx_conversations_booking ON conversations(booking_id);
CREATE INDEX idx_conversations_last_msg ON conversations(last_message_at DESC);

-- Conversation Participants Table
CREATE TABLE conversation_participants (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(30) NOT NULL DEFAULT 'MEMBER', -- GUEST, HOST, SUPPORT_AGENT, AI_BOT
    last_read_at TIMESTAMP,
    unread_count INT NOT NULL DEFAULT 0,
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_conversation_user UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conv_participants_user ON conversation_participants(user_id);
CREATE INDEX idx_conv_participants_conv ON conversation_participants(conversation_id);

-- Messages Table
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT', -- TEXT, IMAGE, DOCUMENT, ACTION_CARD, SYSTEM
    metadata_json TEXT,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at ASC);
CREATE INDEX idx_messages_sender ON messages(sender_id);

-- Message Read Receipts Table
CREATE TABLE message_read_receipts (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    read_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_message_receipt UNIQUE (message_id, user_id)
);

CREATE INDEX idx_read_receipts_message ON message_read_receipts(message_id);
