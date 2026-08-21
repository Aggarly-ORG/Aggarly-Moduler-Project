package com.luna.aggarly.notification.mapper;

import com.luna.aggarly.notification.dto.response.NotificationPreferenceResponse;
import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.dto.response.UserAlertResponse;
import com.luna.aggarly.notification.entity.Notification;
import com.luna.aggarly.notification.entity.UserAlert;
import com.luna.aggarly.notification.entity.UserNotificationPreference;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);

    NotificationPreferenceResponse toResponse(UserNotificationPreference preference);

    List<NotificationPreferenceResponse> toPreferenceResponseList(List<UserNotificationPreference> preferences);

    UserAlertResponse toResponse(UserAlert alert);

    List<UserAlertResponse> toAlertResponseList(List<UserAlert> alerts);
}
