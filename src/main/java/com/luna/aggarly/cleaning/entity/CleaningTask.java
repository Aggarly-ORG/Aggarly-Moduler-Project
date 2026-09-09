package com.luna.aggarly.cleaning.entity;

import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;
import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cleaning_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE cleaning_tasks SET is_deleted = true WHERE id = ?")
public class CleaningTask extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "assigned_cleaner_id")
    private UUID assignedCleanerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CleaningStatus status = CleaningStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private CleaningPriority priority = CleaningPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    @Builder.Default
    private CleaningTaskType taskType = CleaningTaskType.TURNOVER;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_start_time")
    private LocalTime scheduledStartTime;

    @Column(name = "estimated_duration_minutes", nullable = false)
    @Builder.Default
    private int estimatedDurationMinutes = 120;

    @Column(name = "actual_started_at")
    private Instant actualStartedAt;

    @Column(name = "actual_completed_at")
    private Instant actualCompletedAt;

    @Column(name = "cleaner_notes", columnDefinition = "TEXT")
    private String cleanerNotes;

    @Column(name = "host_feedback", columnDefinition = "TEXT")
    private String hostFeedback;

    @Column(name = "rating_by_host")
    private Integer ratingByHost;

    @Column(name = "acoustic_db_reading")
    private Double acousticDbReading;

    @Column(name = "silence_certified")
    @Builder.Default
    private Boolean silenceCertified = false;

    @OneToMany(mappedBy = "cleaningTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CleaningChecklist> checklists = new ArrayList<>();

    @OneToMany(mappedBy = "cleaningTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CleaningPhoto> photos = new ArrayList<>();

    @OneToMany(mappedBy = "cleaningTask", cascade = CascadeType.ALL)
    @Builder.Default
    private List<CleaningIssue> issues = new ArrayList<>();
}
