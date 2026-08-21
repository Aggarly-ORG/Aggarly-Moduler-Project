package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttractionsTool implements Tool<String, List<String>> {

    @Override
    public String name() {
        return "travel.attractions";
    }

    @Override
    public String description() {
        return "Find tourist landmarks, museum exhibits, parks, and cultural sights in a destination.";
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
                "Historical Cathedral & Plaza",
                "Metropolitan Fine Art Museum",
                "Botanical Gardens & Walking Path"
        ));
    }
}
