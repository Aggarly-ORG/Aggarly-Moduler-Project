package com.luna.aggarly.payment.entity;

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

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE webhook_events SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
public class WebhookEvent extends BaseEntity {

    @Column(name = "gateway_event_id", nullable = false, unique = true, length = 255)
    private String gatewayEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;
}