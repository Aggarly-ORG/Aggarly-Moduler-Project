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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionGetPropertyVisualProfileTool implements Tool<VisionGetPropertyVisualProfileTool.Params, VisionGetPropertyVisualProfileTool.VisualProfileResponse> {

    private final PropertyRepository propertyRepository;
    private final PropertyVisualProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public record Params(
            UUID propertyId
    ) {}

    public record VisualProfileResponse(
            UUID propertyId,
            String title,
            String profileStatus,
            int totalImages,
            int usableImages,
            double coverageScore,
            Map<String, Integer> roomCoverage,
            List<String> missingKeyRooms,
            List<String> aggregatedStyleTags,
            List<String> aggregatedAmenities,
            double luxuryScore,
            double romanticScore,
            double familyScore,
            double businessScore,
            double relaxationScore,
            String visualSummary
    ) {}

    @Override
    public String name() {
        return "vision.getPropertyVisualProfile";
    }

    @Override
    public String description() {
        return "Get the comprehensive visual intelligence profile of a property, including room coverage, style tags, confirmed amenities, and lifestyle suitability scores.";
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
    public ToolResult<VisualProfileResponse> execute(Params params, UserPrincipal currentUser) {
        log.info("Executing tool vision.getPropertyVisualProfile for propertyId: {}", params.propertyId());
        Property prop = propertyRepository.findById(params.propertyId()).orElse(null);
        if (prop == null) {
            return ToolResult.failure("Property not found: " + params.propertyId());
        }

        PropertyVisualProfile profile = profileRepository.findByPropertyId(params.propertyId()).orElse(null);
        if (profile == null) {
            return ToolResult.failure("Visual profile has not been generated for property: " + params.propertyId());
        }

        Map<String, Integer> roomCoverage = Map.of();
        List<String> missingRooms = List.of();
        List<String> styles = List.of();
        List<String> amenities = List.of();

        try {
            if (profile.getRoomCoverageJson() != null) roomCoverage = objectMapper.readValue(profile.getRoomCoverageJson(), Map.class);
            if (profile.getMissingKeyRoomsJson() != null) missingRooms = objectMapper.readValue(profile.getMissingKeyRoomsJson(), List.class);
            if (profile.getAggregatedStyleTagsJson() != null) styles = objectMapper.readValue(profile.getAggregatedStyleTagsJson(), List.class);
            if (profile.getAggregatedAmenitiesJson() != null) amenities = objectMapper.readValue(profile.getAggregatedAmenitiesJson(), List.class);
        } catch (Exception ignored) {}

        VisualProfileResponse response = new VisualProfileResponse(
                prop.getId(),
                prop.getTitle(),
                profile.getProfileStatus().name(),
                profile.getTotalImages(),
                profile.getUsableImages(),
                profile.getCoverageScore() != null ? profile.getCoverageScore() : 0.0,
                roomCoverage,
                missingRooms,
                styles,
                amenities,
                profile.getLuxuryScore() != null ? profile.getLuxuryScore() : 0.0,
                profile.getRomanticScore() != null ? profile.getRomanticScore() : 0.0,
                profile.getFamilyScore() != null ? profile.getFamilyScore() : 0.0,
                profile.getBusinessScore() != null ? profile.getBusinessScore() : 0.0,
                profile.getRelaxationScore() != null ? profile.getRelaxationScore() : 0.0,
                profile.getVisualSummary()
        );

        return ToolResult.success(response);
    }
}
