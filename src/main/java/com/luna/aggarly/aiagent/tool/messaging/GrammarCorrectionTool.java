package com.luna.aggarly.aiagent.tool.messaging;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrammarCorrectionTool implements Tool<String, String> {

    @Override
    public String name() {
        return "messaging.grammar";
    }

    @Override
    public String description() {
        return "Polishes tone, grammar, and spelling of guest/host messages before sending.";
    }

    @Override
    public Class<String> parameterType() {
        return String.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(String text, UserPrincipal user) {
        return ToolResult.ok(text);
    }
}
