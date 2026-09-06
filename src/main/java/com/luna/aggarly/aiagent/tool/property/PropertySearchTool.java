package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertySearchTool implements Tool<PropertySearchTool.Params, PropertySearchTool.Response> {

    public record Params(
            @JsonPropertyDescription("City, area, or destination to search properties in (e.g. 'Paris', 'Rome', 'New York').")
            String destination,

            @JsonPropertyDescription("Country name or country code (e.g. 'France', 'US', 'EG').")
            String country,

            @JsonPropertyDescription("Number of guests requiring accommodation.")
            Integer guests,

            @JsonPropertyDescription("Minimum nightly price budget.")
            BigDecimal minPrice,

            @JsonPropertyDescription("Maximum nightly price budget.")
            BigDecimal maxPrice,

            @JsonPropertyDescription("Type of property: APARTMENT, HOUSE, VILLA, STUDIO, CABIN, LOFT, ROOM, CHALET, TOWNHOUSE, OTHER.")
            String propertyType,

            @JsonPropertyDescription("List of required amenities (e.g. ['Wi-Fi', 'Pool', 'Kitchen', 'Air conditioning', 'Free parking']).")
            List<String> amenities,

            @JsonPropertyDescription("Minimum number of bedrooms required.")
            Integer bedrooms,

            @JsonPropertyDescription("Minimum number of bathrooms required.")
            Integer bathrooms,

            @JsonPropertyDescription("Field to sort by: 'price', 'rating', 'createdAt'.")
            String sortBy,

            @JsonPropertyDescription("Sort direction: 'ASC' or 'DESC'.")
            String sortDirection,

            @JsonPropertyDescription("Page number (0-indexed, default is 0).")
            Integer page,

            @JsonPropertyDescription("Number of properties to return per page (default is 10, max is 50).")
            Integer size
    ) {}

    public record Response(
            List<Item> properties,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            String querySummary
    ) {
        public record Item(
                UUID id,
                String title,
                String description,
                String propertyType,
                String status,
                double basePrice,
                int maxGuests,
                int bedrooms,
                int beds,
                int bathrooms,
                double rating,
                int reviewCount,
                String city,
                String country,
                String coverPhotoUrl,
                List<String> images
        ) {}
    }

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
    public Class<Params> parameterType() {
        return Params.class;
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
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        log.info("Executing property.search: destination={}, guests={}, minPrice={}, maxPrice={}",
                params.destination(), params.guests(), params.minPrice(), params.maxPrice());

        LocationFilter locationFilter = null;
        if (params.destination() != null && !params.destination().isBlank()) {
            locationFilter = new LocationFilter(
                    params.destination().trim(),
                    null,
                    params.country() != null && !params.country().isBlank() ? params.country().trim() : null,
                    null, null, null
            );
        } else if (params.country() != null && !params.country().isBlank()) {
            locationFilter = new LocationFilter(null, null, params.country().trim(), null, null, null);
        }

        PricingFilter pricingFilter = null;
        if (params.minPrice() != null || params.maxPrice() != null) {
            pricingFilter = new PricingFilter(params.minPrice(), params.maxPrice(), null);
        }

        CapacityFilter capacityFilter = null;
        if ((params.guests() != null && params.guests() > 0) || params.bedrooms() != null || params.bathrooms() != null) {
            capacityFilter = new CapacityFilter(params.guests(), params.bedrooms(), null, params.bathrooms());
        }

        PropertyTypeFilter typeFilter = null;
        if (params.propertyType() != null && !params.propertyType().isBlank()) {
            try {
                com.luna.aggarly.property.entity.enums.PropertyType pt =
                        com.luna.aggarly.property.entity.enums.PropertyType.valueOf(params.propertyType().trim().toUpperCase());
                typeFilter = new PropertyTypeFilter(Set.of(pt), null);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid propertyType in search: {}", params.propertyType());
            }
        }

        PropertySearchRequest searchRequest = new PropertySearchRequest(
                locationFilter,
                null,
                capacityFilter,
                pricingFilter,
                typeFilter,
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
                null,
                null
        );

        int pageNum = (params.page() != null && params.page() >= 0) ? params.page() : 0;
        int pageSize = (params.size() != null && params.size() > 0) ? Math.min(params.size(), 50) : 10;

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (params.sortBy() != null && !params.sortBy().isBlank()) {
            Sort.Direction direction = "ASC".equalsIgnoreCase(params.sortDirection()) ? Sort.Direction.ASC : Sort.Direction.DESC;
            String field = switch (params.sortBy().toLowerCase()) {
                case "price" -> "basePricePerNight";
                case "rating" -> "avgRating";
                default -> "createdAt";
            };
            sort = Sort.by(direction, field);
        }

        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);
        Page<PropertyResponse> resultPage = propertyService.searchProperties(searchRequest, pageable);

        List<Response.Item> items = resultPage.getContent().stream()
                .map(this::mapToItem)
                .toList();

        String summary = String.format("Found %d properties (page %d of %d).",
                resultPage.getTotalElements(), resultPage.getNumber() + 1, resultPage.getTotalPages());

        Response response = new Response(
                items,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext(),
                summary
        );

        return ToolResult.ok(response);
    }

    private Response.Item mapToItem(PropertyResponse p) {
        String coverPhoto = null;
        List<String> imageUrls = List.of();

        if (p.images() != null && !p.images().isEmpty()) {
            coverPhoto = p.images().stream()
                    .filter(PropertyImageResponse::isCover)
                    .findFirst()
                    .map(PropertyImageResponse::objectKey)
                    .orElse(p.images().get(0).objectKey());

            imageUrls = p.images().stream()
                    .map(PropertyImageResponse::objectKey)
                    .toList();
        }

        return new Response.Item(
                p.id(),
                p.title(),
                p.description(),
                p.propertyType() != null ? p.propertyType().name() : null,
                p.status() != null ? p.status().name() : null,
                p.basePricePerNight() != null ? p.basePricePerNight().doubleValue() : 0.0,
                p.maxGuests(),
                p.bedrooms(),
                p.bedrooms(),
                p.bathrooms(),
                p.avgRating() != null ? p.avgRating().doubleValue() : 0.0,
                p.reviewCount(),
                p.address() != null ? p.address().city() : null,
                p.address() != null ? p.address().country() : null,
                coverPhoto,
                imageUrls
        );
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(Response.class);
    }
}
