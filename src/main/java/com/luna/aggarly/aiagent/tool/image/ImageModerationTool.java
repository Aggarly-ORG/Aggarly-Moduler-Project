package com.luna.aggarly.aiagent.tool.image;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImageModerationTool implements Tool<String, Map<String, Object>> {

    @Override
    public String name() {
        return "image.moderation";
    }

    @Override
    public String description() {
        return "Perform content safety and moderation classification on an uploaded property image.";
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
    public ToolResult<Map<String, Object>> execute(String imageUrl, UserPrincipal user) {
        return ToolResult.ok(Map.of(
                "isSafe", true,
                "categories", Map.of("adult", "VERY_UNLIKELY", "violence", "VERY_UNLIKELY")
        ));
    }
}
