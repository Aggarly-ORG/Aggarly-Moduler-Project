package com.luna.aggarly.aiagent.tool.vision;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.search.ImageSimilaritySearchService;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionSearchByImageTool implements Tool<VisionSearchByImageTool.Params, List<VisionSearchResult>> {

    private final ImageSimilaritySearchService imageSearchService;

    public record Params(
            String referenceObjectKey,
            Integer pageSize,
            String city,
            String country,
            Integer minGuests,
            Double maxPricePerNight
    ) {}

    @Override
    public String name() {
        return "vision.searchByImage";
    }

    @Override
    public String description() {
        return "Find properties visually similar to a reference image uploaded to storage.";
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
        log.info("Executing tool vision.searchByImage with referenceObjectKey: {}", params.referenceObjectKey());
        VisionSearchFilters filters = new VisionSearchFilters(
                params.city(),
                params.country(),
                null,
                null,
                params.minGuests(),
                params.maxPricePerNight(),
                null,
                null,
                null
        );

        List<VisionSearchResult> results = imageSearchService.findSimilarByImageKey(
                params.referenceObjectKey(),
                null,
                filters,
                params.pageSize() != null ? params.pageSize() : 10
        );

        return ToolResult.success(results);
    }
}
