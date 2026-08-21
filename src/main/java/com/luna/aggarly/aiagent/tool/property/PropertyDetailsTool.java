package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertyDetailsParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyDetailsResponse;
import com.luna.aggarly.property.dto.response.AmenityResponse;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyDetailsTool implements Tool<PropertyDetailsParams, PropertyDetailsResponse> {

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.details";
    }

    @Override
    public String description() {
        return "Get comprehensive details of a specific property by its UUID, including full description, address, amenities, capacity, and pricing.";
    }

    @Override
    public Class<PropertyDetailsParams> parameterType() {
        return PropertyDetailsParams.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public ToolResult<PropertyDetailsResponse> execute(PropertyDetailsParams params, UserPrincipal user) {
        log.info("Executing property.details for propertyId={}", params.propertyId());

        try {
            PropertyResponse p = propertyService.getPropertyById(params.propertyId());

            List<String> amenities = p.amenities() != null
                    ? p.amenities().stream().map(AmenityResponse::name).toList()
                    : List.of();

            List<String> rawImageKeys = p.images() != null
                    ? p.images().stream().map(PropertyImageResponse::objectKey).toList()
                    : List.of();

            List<String> formattedImages = rawImageKeys.stream()
                    .map(this::formatImageUrl)
                    .toList();

            Double lat = p.latitude() != null ? p.latitude().doubleValue() : null;
            Double lng = p.longitude() != null ? p.longitude().doubleValue() : null;

            String coverImage = (rawImageKeys != null && !rawImageKeys.isEmpty()) ? rawImageKeys.get(0) : null;
            if (p.images() != null) {
                coverImage = p.images().stream()
                        .filter(PropertyImageResponse::isCover)
                        .findFirst()
                        .map(PropertyImageResponse::objectKey)
                        .orElse(coverImage);
            }
            String formattedCover = formatImageUrl(coverImage);

            PropertyDetailsResponse response = new PropertyDetailsResponse(
                    p.id(),
                    p.title(),
                    p.description(),
                    p.propertyType() != null ? p.propertyType().name() : null,
                    p.maxGuests(),
                    p.bedrooms(),
                    p.bathrooms(),
                    p.basePricePerNight(),
                    p.cancellationPolicy() != null ? p.cancellationPolicy().name() : null,
                    p.status() != null ? p.status().name() : null,
                    p.avgRating(),
                    p.reviewCount(),
                    p.address() != null ? p.address().street() : null,
                    p.address() != null ? p.address().city() : null,
                    p.address() != null ? p.address().state() : null,
                    p.address() != null ? p.address().country() : null,
                    p.address() != null ? p.address().zipCode() : null,
                    lat,
                    lng,
                    amenities,
                    formattedImages,
                    formattedCover,
                    formattedCover,
                    formattedImages,
                    p.hostId()
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to fetch property details for id={}", params.propertyId(), ex);
            return ToolResult.failed("PROPERTY_NOT_FOUND", "Property not found with ID: " + params.propertyId());
        }
    }

    private String formatImageUrl(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("http://") || key.startsWith("https://")) return key;
        return "http://localhost:8081/api/v1/storage/files/view?key=" + key;
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyDetailsParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyDetailsResponse.class);
    }
}
