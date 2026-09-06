package com.luna.aggarly.aiagent.tool.vision;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.dto.PhotoTourWalkthroughResponse;
import com.luna.aggarly.vision.service.PhotoTourSequencingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionGetPhotoTourTool implements Tool<VisionGetPhotoTourTool.Params, PhotoTourWalkthroughResponse> {

    private final PhotoTourSequencingService photoTourSequencingService;
    private final JsonSchemaService jsonSchemaService;

    public record Params(
            @JsonPropertyDescription("UUID of the property to generate a sequenced room-by-room photo tour for.")
            UUID propertyId
    ) {}

    @Override
    public String name() {
        return "vision.getPhotoTour";
    }

    @Override
    public String description() {
        return "Generate a structured, room-by-room sequenced photo walkthrough for a property (Exterior, Living Room, Kitchen, Bedrooms, Bathrooms, Balcony, Pool, View).";
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
    public ToolResult<PhotoTourWalkthroughResponse> execute(Params params, UserPrincipal currentUser) {
        if (params == null || params.propertyId() == null) {
            return ToolResult.failed("INVALID_ARGUMENT", "propertyId is required");
        }
        try {
            PhotoTourWalkthroughResponse tour = photoTourSequencingService.generatePhotoTour(params.propertyId());
            return ToolResult.success(tour);
        } catch (Exception e) {
            log.error("Failed to generate photo tour for property {}", params.propertyId(), e);
            return ToolResult.failed("PHOTO_TOUR_FAILED", e.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
