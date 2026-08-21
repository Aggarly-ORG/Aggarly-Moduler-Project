package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeatherTool implements Tool<WeatherTool.WeatherRequest, Map<String, Object>> {

    public record WeatherRequest(
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
    public Class<WeatherRequest> parameterType() {
        return WeatherRequest.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(WeatherRequest params, UserPrincipal user) {
        return ToolResult.ok(Map.of(
                "location", params.location(),
                "forecast", "Sunny, 24°C (75°F)",
                "precipitationChance", "10%"
        ));
    }
}
