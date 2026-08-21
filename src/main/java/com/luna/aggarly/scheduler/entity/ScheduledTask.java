package com.luna.aggarly.scheduler.entity;

import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.MisfirePolicy;
import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private TaskStatus status = TaskStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false, length = 32)
    @Builder.Default
    private ExecutionType executionType = ExecutionType.DETERMINISTIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "misfire_policy", nullable = false, length = 32)
    @Builder.Default
    private MisfirePolicy misfirePolicy = MisfirePolicy.RUN_ONCE_NOW;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "next_execution_at")
    private Instant nextExecutionAt;

    @Column(name = "last_execution_at")
    private Instant lastExecutionAt;

    @Column(name = "last_started_at")
    private Instant lastStartedAt;

    @Column(name = "last_finished_at")
    private Instant lastFinishedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_config", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String triggerConfig = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String plan = "{}";

    @Column(name = "plan_version", nullable = false)
    @Builder.Default
    private Integer planVersion = 1;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
