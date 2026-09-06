package com.luna.aggarly.aiagent.tool.navigation;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlatformNavigationTool implements Tool<PlatformNavigationTool.Params, Map<String, String>> {

    public record Params(
            String topic
    ) {}

    @Override
    public String name() {
        return "navigation.platformRoute";
    }

    @Override
    public String description() {
        return "Map user feature requests or questions to direct frontend UI route URLs.";
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
    public ToolResult<Map<String, String>> execute(Params params, UserPrincipal user) {
        String topic = (params != null && params.topic() != null) ? params.topic() : "";
        String topicLower = topic.toLowerCase();
        if (topicLower.contains("verify") || topicLower.contains("identity") || topicLower.contains("passport")) {
            return ToolResult.ok(Map.of("targetRoute", "/settings/verification", "label", "Identity Verification Settings"));
        } else if (topicLower.contains("payment") || topicLower.contains("payout") || topicLower.contains("card")) {
            return ToolResult.ok(Map.of("targetRoute", "/settings/payouts", "label", "Payment & Payout Methods"));
        } else if (topicLower.contains("listing") || topicLower.contains("become host")) {
            return ToolResult.ok(Map.of("targetRoute", "/host/create-listing", "label", "Host Property Onboarding"));
        }
        return ToolResult.ok(Map.of("targetRoute", "/help", "label", "Aggarly Help Center"));
    }
}
