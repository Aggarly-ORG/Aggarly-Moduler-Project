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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummaryTool implements Tool<ConversationSummaryTool.Params, String> {

    private final ConversationService conversationService;
    record Params (UUID conversationId){}
    @Override
    public String name() {
        return "messaging.summary";
    }

    @Override
    public String description() {
        return "Summarize key points and action items from a guest-host message thread.";
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
                return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to access conversation history.");
            }
            UUID userId = user.getUserId();
            List<MessageResponse> messages = conversationService.getRecentMessages(params.conversationId, 20, userId);

            if (messages.isEmpty()) {
                return ToolResult.ok("No messages in this conversation yet.");
            }

            String transcript = messages.stream()
                    .map(m -> (m.senderId().equals(userId) ? "Me: " : "Other: ") + m.content())
                    .collect(Collectors.joining("\n"));

            return ToolResult.ok("Summary of " + messages.size() + " messages:\n" + transcript);
        } catch (Exception e) {
            log.error("Failed to summarize conversation", e);
            return ToolResult.failed("CONVERSATION_SUMMARY_ERROR", e.getMessage());
        }
    }
}
