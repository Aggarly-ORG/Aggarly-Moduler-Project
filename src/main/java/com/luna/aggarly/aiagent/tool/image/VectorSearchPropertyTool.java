package com.luna.aggarly.aiagent.tool.image;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VectorSearchPropertyTool implements Tool<VectorSearchPropertyTool.VectorSearchRequest, List<PropertyResponse>> {

    public record VectorSearchRequest(
            String searchPrompt,
            String styleTag,
            int limit
    ) {}

    private final PropertyService propertyService;

    @Override
    public String name() {
        return "image.vectorSearch";
    }

    @Override
    public String description() {
        return "Perform high-dimensional Qdrant vector similarity search over property images and visual features.";
    }

    @Override
    public Class<VectorSearchRequest> parameterType() {
        return VectorSearchRequest.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<List<PropertyResponse>> execute(VectorSearchRequest params, UserPrincipal user) {
        int maxResults = params.limit() > 0 ? Math.min(params.limit(), 20) : 5;
        Page<PropertyResponse> page = propertyService.searchProperties(null, PageRequest.of(0, maxResults));
        return ToolResult.ok(page.getContent());
    }
}
