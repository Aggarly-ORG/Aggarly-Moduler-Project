package com.luna.aggarly.aiagent.tool.vision;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.search.VisionSearchOrchestrator;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionSearchByTextTool implements Tool<VisionSearchByTextTool.Params, List<VisionSearchResult>> {

    private final VisionSearchOrchestrator searchOrchestrator;

    public record Params(
            String query,
            Integer pageSize,
            String cursor,
            String city,
            String country,
            Integer minGuests,
            Double maxPricePerNight,
            List<String> requiredSceneTypes
    ) {}

    @Override
    public String name() {
        return "vision.searchByText";
    }

    @Override
    public String description() {
        return "Search for properties visually and semantically matching a text description of aesthetic, style, or features (e.g. 'modern sea view villa with private pool').";
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
    public ToolResult<List<VisionSearchResult>> execute(Params params, UserPrincipal currentUser) {
        log.info("Executing tool vision.searchByText with query: {}", params.query());
        VisionSearchFilters filters = new VisionSearchFilters(
                params.city(),
                params.country(),
                null,
                null,
                params.minGuests(),
                params.maxPricePerNight(),
                null,
                params.requiredSceneTypes(),
                null
        );

        VisionSearchQuery query = VisionSearchQuery.textQuery(
                params.query(),
                filters,
                params.pageSize() != null ? params.pageSize() : 10,
                params.cursor()
        );

        List<VisionSearchResult> results = searchOrchestrator.search(query);
        return ToolResult.success(results);
    }
}
