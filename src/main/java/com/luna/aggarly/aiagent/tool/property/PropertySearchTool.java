package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertySearchParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertySearchToolResponse;
import com.luna.aggarly.aiagent.tool.property.record.PropertySummaryItem;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.filters.*;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertySearchTool implements Tool<PropertySearchParams, PropertySearchToolResponse> {

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.search";
    }

    @Override
    public String description() {
        return "Search properties by destination city, country, guest capacity, price range, property type, amenities, and bedrooms/bathrooms.";
    }

    @Override
    public Class<PropertySearchParams> parameterType() {
        return PropertySearchParams.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<PropertySearchToolResponse> execute(PropertySearchParams params, UserPrincipal user) {
        log.info("Executing property.search: destination={}, guests={}, minPrice={}, maxPrice={}",
                params.destination(), params.guests(), params.minPrice(), params.maxPrice());

        LocationFilter locationFilter = null;
        if (params.destination() != null || params.country() != null) {
            locationFilter = new LocationFilter(
                    params.destination(),
                    null,
                    params.country(),
                    null,
                    null,
                    null
            );
        }

        CapacityFilter capacityFilter = null;
        if (params.guests() != null || params.bedrooms() != null || params.bathrooms() != null) {
            capacityFilter = new CapacityFilter(
                    params.guests(),
                    params.bedrooms(),
                    null,
                    params.bathrooms()
            );
        }

        PricingFilter pricingFilter = null;
        if (params.minPrice() != null || params.maxPrice() != null) {
            pricingFilter = new PricingFilter(
                    params.minPrice(),
                    params.maxPrice(),
                    null
            );
        }

        PropertyTypeFilter typeFilter = null;
        if (params.propertyType() != null) {
            try {
                typeFilter = new PropertyTypeFilter(
                        Set.of(com.luna.aggarly.property.entity.enums.PropertyType.valueOf(params.propertyType().toUpperCase())),
                        null
                );
            } catch (IllegalArgumentException ignored) {}
        }

        AmenitiesFilter amenitiesFilter = null;
        // AmenitiesFilter takes Set<UUID> amenityIds, so if string amenities are passed, the KeywordSearchFilter or general search handles it

        PropertySearchRequest searchRequest = new PropertySearchRequest(
                locationFilter,
                null,
                capacityFilter,
                pricingFilter,
                typeFilter,
                amenitiesFilter,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        int page = params.page() != null && params.page() >= 0 ? params.page() : 0;
        int size = params.size() != null && params.size() > 0 && params.size() <= 50 ? params.size() : 10;

        Sort sort = Sort.by(Sort.Direction.DESC, "avgRating");
        if ("price".equalsIgnoreCase(params.sortBy())) {
            sort = "asc".equalsIgnoreCase(params.sortDirection())
                    ? Sort.by(Sort.Direction.ASC, "basePricePerNight")
                    : Sort.by(Sort.Direction.DESC, "basePricePerNight");
        } else if ("rating".equalsIgnoreCase(params.sortBy())) {
            sort = Sort.by(Sort.Direction.DESC, "avgRating");
        } else if ("createdAt".equalsIgnoreCase(params.sortBy())) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PropertyResponse> result = propertyService.searchProperties(searchRequest, pageable);

        List<PropertySummaryItem> items = result.getContent().stream()
                .map(this::toSummaryItem)
                .toList();

        PropertySearchToolResponse response = new PropertySearchToolResponse(
                result.getTotalElements(),
                result.getNumber(),
                result.getTotalPages(),
                items
        );

        return ToolResult.ok(response);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertySearchParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertySearchToolResponse.class);
    }

    private PropertySummaryItem toSummaryItem(PropertyResponse p) {
        String city = p.address() != null ? p.address().city() : null;
        String country = p.address() != null ? p.address().country() : null;
        String coverImage = null;

        if (p.images() != null && !p.images().isEmpty()) {
            coverImage = p.images().stream()
                    .filter(PropertyImageResponse::isCover)
                    .findFirst()
                    .map(PropertyImageResponse::objectKey)
                    .orElse(p.images().get(0).objectKey());
        }

        List<String> allImages = (p.images() != null && !p.images().isEmpty())
                ? p.images().stream().map(img -> formatImageUrl(img.objectKey())).toList()
                : List.of();

        String formattedCover = formatImageUrl(coverImage);

        return new PropertySummaryItem(
                p.id(),
                p.title(),
                city,
                country,
                p.propertyType() != null ? p.propertyType().name() : null,
                p.maxGuests(),
                p.bedrooms(),
                p.bathrooms(),
                p.basePricePerNight(),
                p.avgRating(),
                p.reviewCount(),
                formattedCover,
                formattedCover,
                allImages
        );
    }

    private String formatImageUrl(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("http://") || key.startsWith("https://")) return key;
        return "http://localhost:8081/api/v1/storage/files/view?key=" + key;
    }
}
