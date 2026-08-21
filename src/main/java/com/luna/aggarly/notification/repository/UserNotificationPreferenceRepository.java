package com.luna.aggarly.notification.repository;

import com.luna.aggarly.notification.entity.UserNotificationPreference;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {

    List<UserNotificationPreference> findByUserId(UUID userId);

    Optional<UserNotificationPreference> findByUserIdAndCategory(UUID userId, NotificationCategory category);
}
