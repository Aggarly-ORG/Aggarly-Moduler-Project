package com.luna.aggarly.aiagent.tool.messaging;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranslationTool implements Tool<TranslationTool.Params, String> {

    public record Params(
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
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(Params params, UserPrincipal user) {
        String targetLang = (params != null && params.targetLanguage() != null) ? params.targetLanguage() : "English";
        String text = (params != null && params.text() != null) ? params.text() : "";
        return ToolResult.ok("[Translated to " + targetLang + "]: " + text);
    }
}
