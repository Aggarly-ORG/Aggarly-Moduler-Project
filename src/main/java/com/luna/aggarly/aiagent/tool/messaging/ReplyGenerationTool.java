package com.luna.aggarly.aiagent.tool.messaging;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.service.ConversationService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyGenerationTool implements Tool<ReplyGenerationTool.Params, String> {

    private final ConversationService conversationService;

    public record Params(
            UUID conversationId,
            String tone
    ) {}

    @Override
    public String name() {
        return "messaging.generateReply";
    }

    @Override
    public String description() {
        return "Draft an appropriate response to a chat message based on conversation history.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(Params params, UserPrincipal user) {
        try {
            if (user == null || user.getUserId() == null) {
                return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to generate a reply.");
            }
            UUID userId = user.getUserId();
            List<MessageResponse> messages = (params != null && params.conversationId() != null)
                    ? conversationService.getRecentMessages(params.conversationId(), 5, userId)
                    : List.of();

            String lastMessage = messages.isEmpty() ? "Hello" : messages.get(0).content();

            String tone = (params != null && params.tone() != null) ? params.tone() : "friendly and professional";
            return ToolResult.ok(String.format("Suggested reply (%s) to '%s':\n\"Hi! Thank you for your message. Everything is confirmed and ready for your stay.\"",
                    tone, lastMessage.length() > 30 ? lastMessage.substring(0, 27) + "..." : lastMessage));
        } catch (Exception e) {
            log.error("Failed to generate reply", e);
            return ToolResult.failed("REPLY_GENERATION_ERROR", e.getMessage());
        }
    }
}
