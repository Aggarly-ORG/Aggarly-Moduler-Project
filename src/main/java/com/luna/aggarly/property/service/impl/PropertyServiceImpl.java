package com.luna.aggarly.property.service.impl;

import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.property.dto.request.CreatePropertyRequest;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.UpdatePropertyRequest;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.entity.Amenity;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.exceptions.PropertyNotFoundException;
import com.luna.aggarly.property.exceptions.UnauthorizedPropertyAccessException;
import com.luna.aggarly.property.mapper.PropertyMapper;
import com.luna.aggarly.property.repository.AmenityRepository;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.property.repository.PropertySpecification;
import com.luna.aggarly.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Service implementation for property listings management and dynamic search filtering.
 */
@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final AmenityRepository amenityRepository;
    private final PropertyMapper propertyMapper;
    private final FileStorageService fileStorageService;
    private final PropertyImageRepository propertyImageRepository;

    @Override
    @Transactional
    public PropertyResponse createProperty(UUID hostId, CreatePropertyRequest request) {
        Property property = propertyMapper.toEntity(request);
        property.setAddress(propertyMapper.toAddressEntity(request.address()));

        if (request.amenityIds() != null && !request.amenityIds().isEmpty()) {
            List<Amenity> amenitiesList = amenityRepository.findAllById(request.amenityIds());
            property.setAmenities(new HashSet<>(amenitiesList));
        }

        property.setHostId(hostId);
        Property savedProperty = propertyRepository.save(property);

        if (request.imageKeys() != null && !request.imageKeys().isEmpty()) {
            int order = 1;
            for (String key : request.imageKeys()) {
                fileStorageService.markAsActive(key);
                PropertyImage image = PropertyImage.builder()
                        .property(savedProperty)
                        .objectKey(key)
                        .displayOrder(order)
                        .isCover(order == 1)
                        .build();
                propertyImageRepository.save(image);
                order++;
            }
        }

        return propertyMapper.toResponse(savedProperty);
    }

    @Override
    @Transactional
    public PropertyResponse updateProperty(UUID propertyId, UUID hostId, UpdatePropertyRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));

        if (!property.getHostId().equals(hostId)) {
            throw new UnauthorizedPropertyAccessException("You do not have permission to update this property.");
        }

        if (request.title() != null) property.setTitle(request.title());
        if (request.description() != null) property.setDescription(request.description());
        if (request.propertyType() != null) property.setPropertyType(request.propertyType());
        if (request.maxGuests() != null) property.setMaxGuests(request.maxGuests());
        if (request.bedrooms() != null) property.setBedrooms(request.bedrooms());
        if (request.bathrooms() != null) property.setBathrooms(request.bathrooms());
        if (request.basePricePerNight() != null) property.setBasePricePerNight(request.basePricePerNight());
        if (request.cancellationPolicy() != null) property.setCancellationPolicy(request.cancellationPolicy());
        if (request.status() != null) property.setStatus(request.status());

        if (request.amenityIds() != null) {
            List<Amenity> amenitiesList = amenityRepository.findAllById(request.amenityIds());
            property.setAmenities(new HashSet<>(amenitiesList));
        }

        Property updatedProperty = propertyRepository.save(property);
        return propertyMapper.toResponse(updatedProperty);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));
        return propertyMapper.toResponse(property);
    }

    @Override
    @Transactional
    public void deleteProperty(UUID propertyId, UUID hostId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));

        if (!property.getHostId().equals(hostId)) {
            throw new UnauthorizedPropertyAccessException("You do not have permission to delete this property.");
        }

        property.setDeleted(true);
        propertyRepository.save(property);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> searchProperties(PropertySearchRequest searchRequest, Pageable pageable) {
        Specification<Property> spec = PropertySpecification.withFilters(searchRequest);
        Page<Property> propertyPage = propertyRepository.findAll(spec, pageable);
        return propertyPage.map(propertyMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> getHostProperties(UUID hostId, Pageable pageable) {
        Page<Property> propertyPage = propertyRepository.findByHostId(hostId, pageable);
        return propertyPage.map(propertyMapper::toResponse);
    }
}
