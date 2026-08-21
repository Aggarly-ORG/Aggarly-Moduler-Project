package com.luna.aggarly.property.service;

import com.luna.aggarly.property.dto.request.CreatePropertyRequest;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.UpdatePropertyRequest;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for property lifecycle and search operations.
 */
public interface PropertyService {
    
    PropertyResponse createProperty(UUID hostId, CreatePropertyRequest request);
    
    PropertyResponse updateProperty(UUID propertyId, UUID hostId, UpdatePropertyRequest request);
    
    PropertyResponse getPropertyById(UUID propertyId);
    
    void deleteProperty(UUID propertyId, UUID hostId);
    
    Page<PropertyResponse> searchProperties(PropertySearchRequest searchRequest, Pageable pageable);
    
    Page<PropertyResponse> getHostProperties(UUID hostId, Pageable pageable);
}
