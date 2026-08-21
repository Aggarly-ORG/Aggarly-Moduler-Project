package com.luna.aggarly.aiagent.tool.host;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListingOptimizationTool implements Tool<UUID, Map<String, String>> {

    @Override
    public String name() {
        return "host.optimize";
    }

    @Override
    public String description() {
        return "Generate optimized listing titles, descriptions, and SEO keywords for a host's property.";
    }

    @Override
    public Class<UUID> parameterType() {
        return UUID.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, String>> execute(UUID propertyId, UserPrincipal user) {
        return ToolResult.ok(Map.of(
                "suggestedTitle", "Charming & Sunny City Center Apartment w/ Balcony",
                "suggestedDescription", "Experience luxury living in the heart of the city with modern amenities and high-speed Wi-Fi.",
                "seoKeywords", "luxury, city center, balcony, wifi, air conditioning"
        ));
    }
}
