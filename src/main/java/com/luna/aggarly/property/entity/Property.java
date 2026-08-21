package com.luna.aggarly.property.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.property.entity.enums.CancellationPolicy;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.entity.enums.PropertyType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class Property extends BaseEntity {

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 50)
    private PropertyType propertyType;

    @Column(name = "max_guests", nullable = false)
    private int maxGuests;

    @Column(nullable = false)
    private int bedrooms;

    @Column(nullable = false)
    private int bathrooms;

    @Column(name = "base_price_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePricePerNight;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy", nullable = false, length = 50)
    private CancellationPolicy cancellationPolicy;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "review_count")
    @Builder.Default
    private int reviewCount = 0;

    @OneToOne(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Address address;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PropertyImage> images = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "property_amenities",
            joinColumns = @JoinColumn(name = "property_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    @Builder.Default
    private Set<Amenity> amenities = new HashSet<>();


    public void setAddress(Address address) {
        if (address != null) {
            address.setProperty(this);
        }
        this.address = address;
    }

    public void addImage(PropertyImage image) {
        images.add(image);
        image.setProperty(this);
    }

    public void removeImage(PropertyImage image) {
        images.remove(image);
        image.setProperty(null);
    }
}
