package com.luna.aggarly.aiagent.engine;

import com.luna.aggarly.aiagent.engine.records.ChatMessage;
import com.luna.aggarly.aiagent.engine.records.LlmToolCallResponse;
import com.luna.aggarly.aiagent.engine.records.ToolDefinition;

import java.util.List;

public interface LlmClient {

    /**
     * Normal chat completion.
     */
    String chat(List<ChatMessage> messages, String modelOverride);
    
    default String chat(List<ChatMessage> messages) {
        return chat(messages, null);
    }

    /**
     * Chat with tool definitions.
     */
    LlmToolCallResponse chatWithTools(
            List<ChatMessage> messages,
            List<ToolDefinition> tools,
            String modelOverride
    );

    default LlmToolCallResponse chatWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        return chatWithTools(messages, tools, null);
    }

}
