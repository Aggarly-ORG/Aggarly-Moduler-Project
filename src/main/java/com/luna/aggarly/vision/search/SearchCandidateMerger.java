package com.luna.aggarly.vision.search;

import com.luna.aggarly.availability.dto.AvailabilityCheckResponse;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCandidateMerger {

    private final PropertyRepository propertyRepository;
    private final AvailabilityService availabilityService;

    public List<Property> filterAndMergeCandidates(Set<UUID> candidatePropertyIds, VisionSearchFilters filters) {
        if (candidatePropertyIds == null || candidatePropertyIds.isEmpty()) {
            return List.of();
        }

        List<Property> properties = propertyRepository.findAllById(candidatePropertyIds);
        List<Property> passedProperties = new ArrayList<>();

        for (Property p : properties) {
            if (!passesHardConstraints(p, filters)) {
                log.debug("Property {} failed hard constraint filters", p.getId());
                continue;
            }
            passedProperties.add(p);
        }

        return passedProperties;
    }

    public boolean passesHardConstraints(Property p, VisionSearchFilters filters) {
        if (p == null || p.isDeleted()) return false;
        if (p.getStatus() != null && !"ACTIVE".equalsIgnoreCase(p.getStatus().name())) return false;

        if (filters == null) return true;

        // 1. City constraint
        if (filters.city() != null && !filters.city().isBlank()) {
            if (p.getAddress() == null || !filters.city().equalsIgnoreCase(p.getAddress().getCity())) {
                return false;
            }
        }

        // 2. Country constraint
        if (filters.country() != null && !filters.country().isBlank()) {
            if (p.getAddress() == null || !filters.country().equalsIgnoreCase(p.getAddress().getCountry())) {
                return false;
            }
        }

        // 3. Guest Capacity constraint
        if (filters.minGuests() != null && filters.minGuests() > 0) {
            if (p.getMaxGuests() == 0 || p.getMaxGuests() < filters.minGuests()) {
                return false;
            }
        }

        // 4. Price Cap constraint
        if (filters.maxPricePerNight() != null && filters.maxPricePerNight() > 0) {
            if (p.getBasePricePerNight() == null || p.getBasePricePerNight().doubleValue() > filters.maxPricePerNight()) {
                return false;
            }
        }

        // 5. Hard Availability / Dates constraint
        if (filters.checkInDate() != null && filters.checkOutDate() != null) {
            try {
                AvailabilityCheckResponse avail = availabilityService.checkAvailability(p.getId(), filters.checkInDate(), filters.checkOutDate());
                if (avail != null && !avail.available()) {
                    return false;
                }
            } catch (Exception e) {
                log.debug("Availability check error for property {}: {}", p.getId(), e.getMessage());
            }
        }

        return true;
    }
}
