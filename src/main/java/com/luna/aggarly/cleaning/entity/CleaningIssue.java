package com.luna.aggarly.cleaning.entity;

import com.luna.aggarly.cleaning.entity.enums.IssueSeverity;
import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cleaning_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE cleaning_issues SET is_deleted = true WHERE id = ?")
public class CleaningIssue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaning_task_id", nullable = false)
    private CleaningTask cleaningTask;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private IssueSeverity severity = IssueSeverity.MEDIUM;

    @Column(name = "photo_keys", columnDefinition = "TEXT")
    private String photoKeys;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
