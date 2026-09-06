package com.luna.aggarly.aiagent.tool.support;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountHelpTool implements Tool<AccountHelpTool.Params, String> {

    public record Params(
            UUID userId
    ) {}

    @Override
    public String name() {
        return "support.accountHelp";
    }

    @Override
    public String description() {
        return "Provide step-by-step help for password reset, email updates, and notification settings.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(Params params, UserPrincipal user) {
        return ToolResult.ok("Account Management: Navigate to Profile > Security to change your password or update 2FA authentication settings.");
    }
}
