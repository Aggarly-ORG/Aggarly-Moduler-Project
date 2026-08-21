package com.luna.aggarly.property.dto.request;

import com.luna.aggarly.property.dto.request.filters.*;

public record PropertySearchRequest(
        LocationFilter location,
        AvailabilityFilter availability,
        CapacityFilter capacity,
        PricingFilter pricing,
        PropertyTypeFilter propertyType,
        AmenitiesFilter amenities,
        HostAndBookingFilter hostAndBooking,
        AccessibilityFilter accessibility,
        PetsFilter pets,
        SelfCheckInFilter selfCheckIn,
        RatingsFilter ratings,
        ViewsFilter views,
        WorkspaceFilter workspace,
        ParkingFilter parking,
        ImageSearchFilter imageSearch,
        ImageStyleFilter imageStyle,
        ImageMoodFilter imageMood,
        SemanticSearchFilter semanticSearch,
        KeywordSearchFilter keywordSearch,
        ExcludeFilter exclude,
        SortingFilter sorting,
        RankingFilter ranking,
        AiContextFilter aiContext
) {
}
