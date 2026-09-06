package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransportationTool implements Tool<TransportationTool.Params, List<String>> {

    public record Params(
            String location
    ) {}

    @Override
    public String name() {
        return "travel.transportation";
    }

    @Override
    public String description() {
        return "Get public transit, airport shuttle, and rideshare guidance for a location.";
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
                "Metro Line 1 (Direct to City Center - 15 mins)",
                "Airport Express Bus (Departures every 20 mins)",
                "Rideshare pickup available at Terminal 2"
        ));
    }
}
