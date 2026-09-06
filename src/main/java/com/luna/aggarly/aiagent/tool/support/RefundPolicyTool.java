package com.luna.aggarly.aiagent.tool.support;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefundPolicyTool implements Tool<RefundPolicyTool.Params, String> {

    public record Params(
            UUID bookingId
    ) {}

    @Override
    public String name() {
        return "support.refundPolicy";
    }

    @Override
    public String description() {
        return "Explain cancellation policies, refund calculations, and dispute resolution terms.";
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
        return ToolResult.ok("Cancellation Policy: Full refund if cancelled 48+ hours before check-in. 50% refund if cancelled within 24–48 hours.");
    }
}
