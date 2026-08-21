package com.luna.aggarly.property.entity;

import com.luna.aggarly.property.entity.enums.AmenityCategory;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "amenities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Amenity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 100)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AmenityCategory category;
}
