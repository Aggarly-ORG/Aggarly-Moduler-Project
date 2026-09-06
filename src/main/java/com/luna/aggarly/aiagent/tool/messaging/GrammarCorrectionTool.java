package com.luna.aggarly.aiagent.tool.messaging;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrammarCorrectionTool implements Tool<GrammarCorrectionTool.Params, String> {

    public record Params(
            String text
    ) {}

    @Override
    public String name() {
        return "messaging.grammar";
    }

    @Override
    public String description() {
        return "Polishes tone, grammar, and spelling of guest/host messages before sending.";
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
        String text = (params != null && params.text() != null) ? params.text() : "";
        return ToolResult.ok(text);
    }
}
