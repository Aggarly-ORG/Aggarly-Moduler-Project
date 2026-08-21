package com.luna.aggarly.notification.service.impl;

import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.service.InAppPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppPushServiceImpl implements InAppPushService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void pushNotification(UUID userId, NotificationResponse notification) {
        String destination = "/queue/user." + userId + ".notifications";
        try {
            messagingTemplate.convertAndSend(destination, notification);
            log.debug("Pushed in-app notification to WebSocket destination: {}", destination);
        } catch (Exception e) {
            log.error("Failed to push in-app notification to {}", destination, e);
        }
    }
}
