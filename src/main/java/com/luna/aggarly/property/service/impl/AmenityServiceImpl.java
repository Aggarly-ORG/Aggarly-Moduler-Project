package com.luna.aggarly.property.service.impl;

import com.luna.aggarly.property.dto.request.CreateAmenityRequest;
import com.luna.aggarly.property.dto.response.AmenityResponse;
import com.luna.aggarly.property.entity.Amenity;
import com.luna.aggarly.property.exceptions.PropertyNotFoundException;
import com.luna.aggarly.property.mapper.AmenityMapper;
import com.luna.aggarly.property.repository.AmenityRepository;
import com.luna.aggarly.property.service.AmenityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service implementation for managing listing amenities and categories.
 */
@Service
@RequiredArgsConstructor
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final AmenityMapper amenityMapper;

    @Override
    @Transactional
    public AmenityResponse createAmenity(CreateAmenityRequest request) {
        Amenity amenity = Amenity.builder()
                .name(request.name())
                .icon(request.icon())
                .category(request.category())
                .build();
        Amenity saved = amenityRepository.save(amenity);
        return amenityMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AmenityResponse> getAllAmenities() {
        return amenityRepository.findAll().stream()
                .map(amenityMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAmenity(UUID amenityId) {
        if (!amenityRepository.existsById(amenityId)) {
            throw new PropertyNotFoundException("Amenity not found with ID: " + amenityId);
        }
        amenityRepository.deleteById(amenityId);
    }
}
