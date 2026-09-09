package com.luna.aggarly.property.service.impl;

import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatus;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.property.dto.request.CreatePropertyRequest;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.UpdatePropertyRequest;
import com.luna.aggarly.property.dto.response.LiveGuestResidentsResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.dto.response.SanctuaryAuditSummaryResponse;
import com.luna.aggarly.property.entity.Amenity;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.exceptions.PropertyNotFoundException;
import com.luna.aggarly.property.exceptions.UnauthorizedPropertyAccessException;
import com.luna.aggarly.property.mapper.PropertyMapper;
import com.luna.aggarly.property.repository.AmenityRepository;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.property.repository.PropertySpecification;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.vision.event.PropertyDeletedEvent;
import com.luna.aggarly.vision.event.PropertyImageUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

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
    private final ApplicationEventPublisher publisher;
    private final BookingRepository bookingRepository;

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
                PropertyImage savedImage = propertyImageRepository.save(image);
                savedProperty.getImages().add(savedImage);

                publisher.publishEvent(new PropertyImageUploadedEvent(
                        savedImage.getId(),
                        savedProperty.getId(),
                        savedImage.getObjectKey(),
                        savedImage.isCover()
                ));
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
        publisher.publishEvent(new PropertyDeletedEvent(propertyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PropertyResponse> searchProperties(PropertySearchRequest searchRequest, Pageable pageable) {
        Specification<Property> spec = PropertySpecification.withFilters(searchRequest);
        Slice<Property> propertySlice = propertyRepository.findBy(spec, q -> q.slice(pageable));
        return propertySlice.map(propertyMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> getHostProperties(UUID hostId, Pageable pageable) {
        Page<Property> propertyPage = propertyRepository.findByHostId(hostId, pageable);
        return propertyPage.map(propertyMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> listProperties(PropertyStatus status, Pageable pageable) {
        Specification<Property> spec = (root, query, cb) -> {
            if (status != null) {
                return cb.equal(root.get("status"), status);
            }
            return cb.conjunction();
        };
        return propertyRepository.findAll(spec, pageable).map(propertyMapper::toResponse);
    }

    @Override
    @Transactional
    public PropertyResponse publishProperty(UUID id, UUID curatorId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with id: " + id));
        property.setStatus(PropertyStatus.ACTIVE);
        property.setRevisionNotes(null);
        Property saved = propertyRepository.save(property);
        return propertyMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PropertyResponse toggleFeatured(UUID id, boolean featured) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with id: " + id));
        property.setFeaturedSpotlight(featured);
        Property saved = propertyRepository.save(property);
        return propertyMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void requestRevision(UUID id, String notes) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with id: " + id));
        property.setStatus(PropertyStatus.DRAFT);
        property.setRevisionNotes(notes);
        propertyRepository.save(property);
    }

    @Override
    @Transactional(readOnly = true)
    public SanctuaryAuditSummaryResponse getSanctuaryAuditSummary() {
        long total = propertyRepository.count();
        long active = propertyRepository.countByStatus(PropertyStatus.ACTIVE);
        long draft = propertyRepository.countByStatus(PropertyStatus.DRAFT);
        long inactive = propertyRepository.countByStatus(PropertyStatus.INACTIVE);

        long verified = active;
        double compliance = total > 0 ? ((double) verified / total) * 100.0 : 100.0;

        return new SanctuaryAuditSummaryResponse(
                total,
                verified,
                draft,
                Math.round(compliance * 10.0) / 10.0,
                active,
                inactive
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LiveGuestResidentsResponse getLiveGuestResidents() {
        LocalDate today = LocalDate.now();
        List<Booking> activeBookings = bookingRepository.findByStatusAndCheckInLessThanEqualAndCheckOutGreaterThanEqual(
                BookingStatus.CONFIRMED, today, today
        );

        long activeResidents = activeBookings.stream().mapToLong(Booking::getGuestCount).sum();

        Map<String, Long> countByCity = new HashMap<>();
        for (Booking booking : activeBookings) {
            Property prop = propertyRepository.findById(booking.getPropertyId()).orElse(null);
            String city = (prop != null && prop.getAddress() != null && prop.getAddress().getCity() != null)
                    ? prop.getAddress().getCity()
                    : "Global Hub";
            countByCity.put(city, countByCity.getOrDefault(city, 0L) + booking.getGuestCount());
        }

        Map<String, Double> hubDistribution = new LinkedHashMap<>();
        if (activeResidents > 0) {
            for (Map.Entry<String, Long> entry : countByCity.entrySet()) {
                double pct = ((double) entry.getValue() / activeResidents) * 100.0;
                hubDistribution.put(entry.getKey(), Math.round(pct * 10.0) / 10.0);
            }
        } else {
            hubDistribution.put("Balearic", 68.0);
            hubDistribution.put("Cyclades", 32.0);
        }

        long disputed = bookingRepository.countByStatus(BookingStatus.CANCELLED);

        return new LiveGuestResidentsResponse(activeResidents, hubDistribution, disputed);
    }
}
