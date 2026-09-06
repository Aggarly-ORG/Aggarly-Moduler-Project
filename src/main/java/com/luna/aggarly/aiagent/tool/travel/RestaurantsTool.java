package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RestaurantsTool implements Tool<RestaurantsTool.Params, List<String>> {

    public record Params(
            String location,
            String cuisine
    ) {}

    @Override
    public String name() {
        return "travel.restaurants";
    }

    @Override
    public String description() {
        return "Discover top-rated dining, cafes, and local cuisine options in a specified city/neighborhood.";
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
    public ToolResult<List<String>> execute(Params params, UserPrincipal user) {
        return ToolResult.ok(List.of(
                "Trattoria Bella Vista (Italian, ★ 4.9)",
                "Le Petit Cafe (Bakery & Coffee, ★ 4.8)",
                "El Tapas Central (Tapas Bar, ★ 4.7)"
        ));
    }
}
