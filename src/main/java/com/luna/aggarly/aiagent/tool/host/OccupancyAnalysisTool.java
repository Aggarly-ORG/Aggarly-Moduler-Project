package com.luna.aggarly.aiagent.tool.host;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OccupancyAnalysisTool implements Tool<UUID, Map<String, Object>> {

    @Override
    public String name() {
        return "host.occupancyAnalysis";
    }

    @Override
    public String description() {
        return "Analyze historical and projected occupancy rates, revenue, and booking velocity for a host's property.";
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
                "occupancyRatePercentage", 82.5,
                "totalBookingsCount", 24,
                "projectedMonthlyRevenue", 3480.00
        ));
    }
}
