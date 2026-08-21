package com.luna.aggarly.aiagent.tool.vision;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.search.VisionSearchOrchestrator;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionSearchSimilarToPropertyTool implements Tool<VisionSearchSimilarToPropertyTool.Params, List<VisionSearchResult>> {

    private final PropertyVisualProfileRepository profileRepository;
    private final VisionSearchOrchestrator searchOrchestrator;

    public record Params(
            UUID sourcePropertyId,
            Integer pageSize,
            List<String> requiredSceneTypes
    ) {}

    @Override
    public String name() {
        return "vision.searchSimilarToProperty";
    }

    @Override
    public String description() {
        return "Find properties that are visually and stylistically similar to an existing property that the user liked.";
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
        log.info("Executing tool vision.searchSimilarToProperty for sourcePropertyId={}", params.sourcePropertyId());
        PropertyVisualProfile profile = profileRepository.findByPropertyId(params.sourcePropertyId()).orElse(null);
        if (profile == null) {
            return ToolResult.failure("Visual profile not found for source property: " + params.sourcePropertyId());
        }

        String searchSummary = profile.getVisualSummary() != null ? profile.getVisualSummary() : "Luxury contemporary property";

        VisionSearchFilters filters = new VisionSearchFilters(
                null, null, null, null, null, null, null, params.requiredSceneTypes(), null
        );

        VisionSearchQuery query = VisionSearchQuery.textQuery(
                searchSummary,
                filters,
                params.pageSize() != null ? params.pageSize() : 10,
                null
        );

        List<VisionSearchResult> results = searchOrchestrator.search(query).stream()
                .filter(r -> !r.propertyId().equals(params.sourcePropertyId()))
                .toList();

        return ToolResult.success(results);
    }
}
