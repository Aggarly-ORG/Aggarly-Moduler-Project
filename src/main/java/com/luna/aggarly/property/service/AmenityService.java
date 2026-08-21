package com.luna.aggarly.property.service;

import com.luna.aggarly.property.dto.request.CreateAmenityRequest;
import com.luna.aggarly.property.dto.response.AmenityResponse;

import java.util.List;
import java.util.UUID;

public interface AmenityService {
    
    AmenityResponse createAmenity(CreateAmenityRequest request);
    
    List<AmenityResponse> getAllAmenities();
    
    void deleteAmenity(UUID amenityId);
}
