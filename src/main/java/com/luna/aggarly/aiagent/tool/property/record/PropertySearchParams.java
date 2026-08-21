package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;

public record PropertySearchParams(
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
