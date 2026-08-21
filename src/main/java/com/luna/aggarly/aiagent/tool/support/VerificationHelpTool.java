package com.luna.aggarly.aiagent.tool.support;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VerificationHelpTool implements Tool<UUID, String> {

    @Override
    public String name() {
        return "support.verificationHelp";
    }

    @Override
    public String description() {
        return "Guide user through identity verification (ID upload, host verification steps).";
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
    public ToolResult<String> execute(UUID userId, UserPrincipal user) {
        return ToolResult.ok("Identity Verification Steps: 1. Go to Account Settings > Verification. 2. Upload a government-issued photo ID. Verification completes in 5-10 minutes.");
    }
}
