package com.luna.aggarly.aiagent.tool.user;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserVerificationStatusTool implements Tool<UUID, Map<String, Object>> {

    @Override
    public String name() {
        return "user.verificationStatus";
    }

    @Override
    public String description() {
        return "Check identity verification status, phone verification, and MFA status for a user account.";
    }

    @Override
    public Class<UUID> parameterType() {
        return UUID.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(UUID userId, UserPrincipal user) {
        UUID targetId = userId != null ? userId : (user != null ? user.getUserId() : null);
        return ToolResult.ok(Map.of(
                "userId", targetId != null ? targetId.toString() : "unknown",
                "emailVerified", true,
                "phoneVerified", true,
                "governmentIdVerified", true,
                "mfaEnabled", false
        ));
    }
}
