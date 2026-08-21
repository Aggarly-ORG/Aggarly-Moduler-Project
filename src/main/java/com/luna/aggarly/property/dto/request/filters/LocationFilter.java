package com.luna.aggarly.property.dto.request.filters;

public record LocationFilter ( String city, String state, String country, Double latitude, Double longitude, Double radiusKm ) {}
