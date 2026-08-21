package com.luna.aggarly.notification.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_notif_pref", columnNames = {"user_id", "category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE user_notification_preferences SET is_deleted = true WHERE id = ?")
public class UserNotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private boolean smsEnabled = false;
}
