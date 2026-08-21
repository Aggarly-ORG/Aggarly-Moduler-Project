package com.luna.aggarly.property.repository;

import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.entity.Address;
import com.luna.aggarly.property.entity.Amenity;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.enums.AmenityMatchType;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PropertySpecification {

    private PropertySpecification() {
    }

    public static Specification<Property> withFilters(PropertySearchRequest filter) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            addStatusFilter(root, cb, predicates);

            addLocationFilters(filter, root, cb, predicates);

            addCapacityFilters(filter, root, cb, predicates);

            addPriceFilters(filter, root, cb, predicates);

            addPropertyTypeFilter(filter, root, predicates);

            addAmenityFilter(filter, root, query, cb, predicates);

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addStatusFilter(
            Root<Property> root,
            CriteriaBuilder cb,
            List<Predicate> predicates
    ) {
        predicates.add(
                cb.equal(root.get("status"), PropertyStatus.ACTIVE)
        );
    }

    private static void addLocationFilters(
            PropertySearchRequest filter,
            Root<Property> root,
            CriteriaBuilder cb,
            List<Predicate> predicates
    ) {
        if (filter.location() == null) {
            return;
        }

        if (isBlank(filter.location().city()) && isBlank(filter.location().country())) {
            return;
        }

        Join<Property, Address> address =
                root.join("address", JoinType.INNER);

        if (!isBlank(filter.location().city())) {
            predicates.add(
                    cb.equal(
                            cb.lower(address.get("city")),
                            filter.location().city().trim().toLowerCase()
                    )
            );
        }

        if (!isBlank(filter.location().country())) {
            predicates.add(
                    cb.equal(
                            cb.lower(address.get("country")),
                            filter.location().country().trim().toLowerCase()
                    )
            );
        }
    }

    private static void addCapacityFilters(
            PropertySearchRequest filter,
            Root<Property> root,
            CriteriaBuilder cb,
            List<Predicate> predicates
    ) {
        if (filter.capacity() == null) {
            return;
        }

        if (filter.capacity().guests() != null) {
            predicates.add(
                    cb.greaterThanOrEqualTo(
                            root.get("maxGuests"),
                            filter.capacity().guests()
                    )
            );
        }

        if (filter.capacity().minBedrooms() != null) {
            predicates.add(
                    cb.greaterThanOrEqualTo(
                            root.get("bedrooms"),
                            filter.capacity().minBedrooms()
                    )
            );
        }
    }

    private static void addPriceFilters(
            PropertySearchRequest filter,
            Root<Property> root,
            CriteriaBuilder cb,
            List<Predicate> predicates
    ) {
        if (filter.pricing() == null) {
            return;
        }

        if (filter.pricing().minPrice() != null) {
            predicates.add(
                    cb.greaterThanOrEqualTo(
                            root.get("basePricePerNight"),
                            filter.pricing().minPrice()
                    )
            );
        }

        if (filter.pricing().maxPrice() != null) {
            predicates.add(
                    cb.lessThanOrEqualTo(
                            root.get("basePricePerNight"),
                            filter.pricing().maxPrice()
                    )
            );
        }
    }

    private static void addPropertyTypeFilter(
            PropertySearchRequest filter,
            Root<Property> root,
            List<Predicate> predicates
    ) {
        if (filter.propertyType() == null) {
            return;
        }

        if (filter.propertyType().propertyTypes() != null &&
                !filter.propertyType().propertyTypes().isEmpty()) {

            predicates.add(
                    root.get("propertyType")
                            .in(filter.propertyType().propertyTypes())
            );
        }
    }

    private static void addAmenityFilter(
            PropertySearchRequest filter,
            Root<Property> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            List<Predicate> predicates
    ) {
        if (filter.amenities() == null) {
            return;
        }

        if (filter.amenities().amenityIds() == null ||
                filter.amenities().amenityIds().isEmpty()) {
            return;
        }

        if (filter.amenities().amenityMatchType() == AmenityMatchType.CONTAINS) {
            containsAmenities(
                    filter.amenities().amenityIds(),
                    root,
                    query,
                    cb,
                    predicates
            );
        } else {
            throw new UnsupportedOperationException(
                    "EXACT amenity matching requires a correlated subquery and is not implemented."
            );
        }
    }

    /**
     * Property contains ALL requested amenities,
     * but may contain additional amenities.
     */
    private static void containsAmenities(
            java.util.Set<UUID> amenityIds,
            Root<Property> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            List<Predicate> predicates
    ) {

        Join<Property, Amenity> amenities =
                root.join("amenities", JoinType.INNER);

        predicates.add(
                amenities.get("id").in(amenityIds)
        );

        query.groupBy(root.get("id"));

        query.having(
                cb.equal(
                        cb.countDistinct(amenities.get("id")),
                        amenityIds.size()
                )
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}