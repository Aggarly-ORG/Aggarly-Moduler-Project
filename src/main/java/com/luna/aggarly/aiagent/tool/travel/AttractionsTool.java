package com.luna.aggarly.aiagent.tool.travel;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttractionsTool implements Tool<AttractionsTool.Params, List<String>> {

    public record Params(
            String location
    ) {}

    @Override
    public String name() {
        return "travel.attractions";
    }

    @Override
    public String description() {
        return "Find tourist landmarks, museum exhibits, parks, and cultural sights in a destination.";
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
                "Historical Cathedral & Plaza",
                "Metropolitan Fine Art Museum",
                "Botanical Gardens & Walking Path"
        ));
    }
}
