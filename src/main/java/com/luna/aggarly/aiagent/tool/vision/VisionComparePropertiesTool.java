package com.luna.aggarly.aiagent.tool.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionComparePropertiesTool implements Tool<VisionComparePropertiesTool.Params, VisionComparePropertiesTool.ComparisonResponse> {

    private final PropertyRepository propertyRepository;
    private final PropertyVisualProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public record Params(
            List<UUID> propertyIds
    ) {}

    public record PropertyVisualCard(
            UUID propertyId,
            String title,
            String city,
            Double pricePerNight,
            Double coverageScore,
            List<String> topStyleTags,
            List<String> confirmedAmenities,
            double luxuryScore,
            double romanticScore,
            double familyScore,
            String visualSummary
    ) {}

    public record ComparisonResponse(
            List<PropertyVisualCard> properties,
            String comparativeVerdict
    ) {}

    @Override
    public String name() {
        return "vision.compareProperties";
    }

    @Override
    public String description() {
        return "Compare the visual aesthetics, room coverage, style tags, and verified amenities of 2-4 properties side-by-side.";
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
    public ToolResult<ComparisonResponse> execute(Params params, UserPrincipal currentUser) {
        log.info("Executing tool vision.compareProperties for ids: {}", params.propertyIds());
        List<PropertyVisualCard> cards = new ArrayList<>();

        if (params.propertyIds() == null || params.propertyIds().isEmpty()) {
            return ToolResult.success(new ComparisonResponse(List.of(), "No property IDs specified for comparison."));
        }

        for (UUID propId : params.propertyIds()) {
            Property prop = propertyRepository.findById(propId).orElse(null);
            if (prop == null) continue;

            PropertyVisualProfile profile = profileRepository.findByPropertyId(propId).orElse(null);

            List<String> styles = new ArrayList<>();
            List<String> amenities = new ArrayList<>();

            if (profile != null) {
                try {
                    if (profile.getAggregatedStyleTagsJson() != null) {
                        styles = objectMapper.readValue(profile.getAggregatedStyleTagsJson(), List.class);
                    }
                    if (profile.getAggregatedAmenitiesJson() != null) {
                        amenities = objectMapper.readValue(profile.getAggregatedAmenitiesJson(), List.class);
                    }
                } catch (Exception ignored) {}
            }

            cards.add(new PropertyVisualCard(
                    prop.getId(),
                    prop.getTitle(),
                    prop.getAddress() != null ? prop.getAddress().getCity() : "",
                    prop.getBasePricePerNight() != null ? prop.getBasePricePerNight().doubleValue() : 0.0,
                    profile != null ? profile.getCoverageScore() : 0.0,
                    styles,
                    amenities,
                    profile != null && profile.getLuxuryScore() != null ? profile.getLuxuryScore() : 0.0,
                    profile != null && profile.getRomanticScore() != null ? profile.getRomanticScore() : 0.0,
                    profile != null && profile.getFamilyScore() != null ? profile.getFamilyScore() : 0.0,
                    profile != null ? profile.getVisualSummary() : "Standard visual presentation."
            ));
        }

        String verdict = String.format("Compared %d properties. Visual profiles and style breakdown generated above.", cards.size());
        return ToolResult.success(new ComparisonResponse(cards, verdict));
    }
}
