package com.luna.aggarly.vision.entity;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.entity.enums.VisionTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vision_processing_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionProcessingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_image_id")
    private PropertyImage propertyImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    private VisionTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VisionTaskStatus status = VisionTaskStatus.QUEUED;

    @Column(name = "current_stage", length = 50)
    private String currentStage;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 5;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "retry_policy", length = 20)
    private String retryPolicy;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "worker_instance_id", length = 200)
    private String workerInstanceId;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "scheduled_at")
    @Builder.Default
    private Instant scheduledAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
