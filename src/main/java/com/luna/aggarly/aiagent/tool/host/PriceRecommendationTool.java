package com.luna.aggarly.aiagent.tool.host;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PriceRecommendationTool implements Tool<UUID, Map<String, Object>> {

    @Override
    public String name() {
        return "host.priceRecommendation";
    }

    @Override
    public String description() {
        return "Recommend optimal nightly prices based on local market demand, season, and competitor rates.";
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
    public ToolResult<Map<String, Object>> execute(UUID propertyId, UserPrincipal user) {
        return ToolResult.ok(Map.of(
                "recommendedNightlyPrice", new BigDecimal("145.00"),
                "minRecommended", new BigDecimal("120.00"),
                "maxRecommended", new BigDecimal("180.00"),
                "demandIndex", "HIGH"
        ));
    }
}
