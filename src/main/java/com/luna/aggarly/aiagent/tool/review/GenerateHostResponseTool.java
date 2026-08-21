package com.luna.aggarly.aiagent.tool.review;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GenerateHostResponseTool implements Tool<GenerateHostResponseTool.GenerateHostResponseRequest, String> {

    public record GenerateHostResponseRequest(
            UUID reviewId,
            String tone
    ) {}

    @Override
    public String name() {
        return "review.generateHostResponse";
    }

    @Override
    public String description() {
        return "Draft a polite, professional host response to a guest review.";
    }

    @Override
    public Class<GenerateHostResponseRequest> parameterType() {
        return GenerateHostResponseRequest.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(GenerateHostResponseRequest params, UserPrincipal user) {
        String draft = "Thank you so much for your review! It was a pleasure hosting you, and we hope to welcome you back again soon.";
        return ToolResult.ok(draft);
    }
}
