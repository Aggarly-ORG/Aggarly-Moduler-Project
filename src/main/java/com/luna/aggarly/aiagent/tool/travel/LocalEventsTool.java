package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LocalEventsTool implements Tool<String, List<String>> {

    @Override
    public String name() {
        return "travel.localEvents";
    }

    @Override
    public String description() {
        return "Find upcoming concerts, festivals, and cultural events taking place in a city.";
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
                "Summer Jazz & Wine Festival (Plaza Major, July 15-18)",
                "International Food & Food Truck Expo (River Park, July 20)"
        ));
    }
}
