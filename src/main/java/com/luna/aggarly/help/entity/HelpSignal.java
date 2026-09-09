package com.luna.aggarly.help.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "help_signals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpSignal extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "residency_ref", length = 100)
    private String residencyRef;

    @Column(name = "situation_type", nullable = false, length = 100)
    private String situationType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "DISPATCHED";

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}