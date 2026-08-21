package com.luna.aggarly.property.service;

import com.luna.aggarly.property.dto.request.AddPropertyImageRequest;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;

import java.util.UUID;

/**
 * Service interface for managing property images and cover photo assignments.
 */
public interface PropertyImageService {
    
    PropertyImageResponse addImage(UUID propertyId, UUID hostId, AddPropertyImageRequest request);
    
    void deleteImage(UUID propertyId, UUID hostId, UUID imageId);
    
    PropertyImageResponse setCoverImage(UUID propertyId, UUID hostId, UUID imageId);
}
