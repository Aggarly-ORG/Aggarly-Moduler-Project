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

@Component
@RequiredArgsConstructor
public class SimilarPropertySearchTool implements Tool<String, Page<PropertyResponse>> {

    private final PropertyService propertyService;

    @Override
    public String name() {
        return "image.similarProperties";
    }

    @Override
    public String description() {
        return "Search for properties visually similar in interior design and architecture to an uploaded image.";
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
    public ToolResult<Page<PropertyResponse>> execute(String imageUrl, UserPrincipal user) {
        Page<PropertyResponse> response = propertyService.searchProperties(null, PageRequest.of(0, 5));
        return ToolResult.ok(response);
    }
}
