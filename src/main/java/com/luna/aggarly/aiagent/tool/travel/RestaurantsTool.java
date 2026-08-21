package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RestaurantsTool implements Tool<String, List<String>> {

    @Override
    public String name() {
        return "travel.restaurants";
    }

    @Override
    public String description() {
        return "Discover top-rated dining, cafes, and local cuisine options in a specified city/neighborhood.";
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
    public ToolResult<List<String>> execute(String location, UserPrincipal user) {
        return ToolResult.ok(List.of(
                "Trattoria Bella Vista (Italian, ★ 4.9)",
                "Le Petit Cafe (Bakery & Coffee, ★ 4.8)",
                "El Tapas Central (Tapas Bar, ★ 4.7)"
        ));
    }
}
