package com.luna.aggarly.availability.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "availability_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilitySlot extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "date_range", columnDefinition = "daterange", insertable = false, updatable = false)
    private String dateRange; 

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate; 

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;   

    @Column(name = "available", nullable = false)
    private boolean available; 

    @Enumerated(EnumType.STRING)
    @Column(name = "block_reason")
    private BlockReason blockReason; 

    @Column(name = "booking_id")
    private UUID bookingId; 
}
