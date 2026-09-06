package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeatherTool implements Tool<WeatherTool.Params, Map<String, Object>> {

    public record Params(
            String location,
            String dateRange
    ) {}

    @Override
    public String name() {
        return "travel.weather";
    }

    @Override
    public String description() {
        return "Fetch weather forecast (temperature, precipitation, conditions) for a location and date range.";
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
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        String loc = (params != null && params.location() != null) ? params.location() : "City Center";
        return ToolResult.ok(Map.of(
                "location", loc,
                "forecast", "Sunny, 24°C (75°F)",
                "precipitationChance", "10%"
        ));
    }
}
