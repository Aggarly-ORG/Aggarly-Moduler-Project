CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    title VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_calls_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_ai_messages_conversation_id_created_at ON ai_messages(conversation_id, created_at);

CREATE TABLE ai_tool_invocations (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id),
    user_id UUID NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    parameters_json TEXT,
    result_summary_json TEXT,
    success BOOLEAN NOT NULL,
    error_code VARCHAR(100),
    duration_ms BIGINT NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    estimated_cost_usd DOUBLE PRECISION,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_ai_tool_invocations_user_id_executed_at ON ai_tool_invocations(user_id, executed_at);
CREATE INDEX idx_ai_tool_invocations_conversation_id ON ai_tool_invocations(conversation_id);

CREATE TABLE ai_search_contexts (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL UNIQUE REFERENCES ai_conversations(id),
    filters_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE ai_user_memories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    memory_key VARCHAR(255) NOT NULL,
    memory_value TEXT NOT NULL,
    user_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_ai_user_memories_user_id_memory_key ON ai_user_memories(user_id, memory_key);
