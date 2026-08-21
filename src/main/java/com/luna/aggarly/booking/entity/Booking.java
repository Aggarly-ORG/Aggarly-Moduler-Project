package com.luna.aggarly.booking.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE bookings SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
public class Booking extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price_breakdown_json", columnDefinition = "TEXT", nullable = false)
    private String priceBreakdownJson;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
}
