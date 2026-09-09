package com.luna.aggarly.property.service;

import com.luna.aggarly.property.dto.request.CreatePropertyRequest;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.UpdatePropertyRequest;
import com.luna.aggarly.property.dto.response.LiveGuestResidentsResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.dto.response.SanctuaryAuditSummaryResponse;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

/**
 * Service interface for property lifecycle and search operations.
 */
public interface PropertyService {
    
    PropertyResponse createProperty(UUID hostId, CreatePropertyRequest request);
    
    PropertyResponse updateProperty(UUID propertyId, UUID hostId, UpdatePropertyRequest request);
    
    PropertyResponse getPropertyById(UUID propertyId);
    
    void deleteProperty(UUID propertyId, UUID hostId);
    
    Slice<PropertyResponse> searchProperties(PropertySearchRequest searchRequest, Pageable pageable);
    
    Page<PropertyResponse> getHostProperties(UUID hostId, Pageable pageable);

    Page<PropertyResponse> listProperties(PropertyStatus status, Pageable pageable);

    PropertyResponse publishProperty(UUID id, UUID curatorId);

    PropertyResponse toggleFeatured(UUID id, boolean featured);

    void requestRevision(UUID id, String notes);

    SanctuaryAuditSummaryResponse getSanctuaryAuditSummary();

    LiveGuestResidentsResponse getLiveGuestResidents();
}
