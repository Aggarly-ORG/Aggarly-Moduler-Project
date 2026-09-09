package com.luna.aggarly.review.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE reviews SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
public class Review extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", columnDefinition = "TEXT", nullable = false)
    private String comment;

    @Column(name = "cleanliness_rating")
    private Integer cleanlinessRating;

    @Column(name = "accuracy_rating")
    private Integer accuracyRating;

    @Column(name = "check_in_rating")
    private Integer checkInRating;

    @Column(name = "communication_rating")
    private Integer communicationRating;

    @Column(name = "location_rating")
    private Integer locationRating;

    @Column(name = "value_rating")
    private Integer valueRating;

    @Column(name = "quietude_rating")
    private Integer quietudeRating;

    @Column(name = "optics_rating")
    private Integer opticsRating;

    @Column(name = "host_response", columnDefinition = "TEXT")
    private String hostResponse;

    @Column(name = "host_responded_at")
    private java.time.Instant hostRespondedAt;
}
