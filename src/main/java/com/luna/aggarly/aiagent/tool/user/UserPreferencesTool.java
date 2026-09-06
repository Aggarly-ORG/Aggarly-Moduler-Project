package com.luna.aggarly.aiagent.tool.user;

import com.luna.aggarly.aiagent.engine.MemoryContextManager;
import com.luna.aggarly.aiagent.entity.AiUserMemory;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPreferencesTool implements Tool<UserPreferencesTool.Params, List<AiUserMemory>> {

    public record Params(
            UUID userId
    ) {}

    private final MemoryContextManager memoryContextManager;

    @Override
    public String name() {
        return "user.preferences";
    }

    @Override
    public String description() {
        return "Fetch stored long-term AI memories and preferences for a user.";
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
    public ToolResult<List<AiUserMemory>> execute(Params params, UserPrincipal user) {
        UUID targetUserId = (params != null && params.userId() != null)
                ? params.userId()
                : (user != null ? user.getUserId() : null);
        if (targetUserId == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to access preferences.");
        }
        List<AiUserMemory> memories = memoryContextManager.listMemories(targetUserId);
        return ToolResult.ok(memories);
    }
}
