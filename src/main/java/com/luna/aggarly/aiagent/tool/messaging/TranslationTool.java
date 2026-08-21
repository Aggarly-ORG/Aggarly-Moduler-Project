package com.luna.aggarly.aiagent.tool.messaging;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranslationTool implements Tool<TranslationTool.TranslationRequest, String> {

    public record TranslationRequest(
            String text,
            String targetLanguage
    ) {}

    @Override
    public String name() {
        return "messaging.translate";
    }

    @Override
    public String description() {
        return "Translate guest or host messages into a specified target language.";
    }

    @Override
    public Class<TranslationRequest> parameterType() {
        return TranslationRequest.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(TranslationRequest params, UserPrincipal user) {
        return ToolResult.ok("[Translated to " + params.targetLanguage() + "]: " + params.text());
    }
}
