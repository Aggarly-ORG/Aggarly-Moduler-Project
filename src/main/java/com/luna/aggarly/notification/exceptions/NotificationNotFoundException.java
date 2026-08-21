package com.luna.aggarly.notification.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class NotificationNotFoundException extends AggarlyException {
    public NotificationNotFoundException(UUID id) {
        super("Notification not found: " + id, HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND");
    }
}
