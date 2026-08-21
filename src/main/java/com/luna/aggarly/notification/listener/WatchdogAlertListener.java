package com.luna.aggarly.notification.listener;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.event.AiAlertTriggeredEvent;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchdogAlertListener {

    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiAlertTriggered(AiAlertTriggeredEvent event) {
        log.info("Processing AiAlertTriggeredEvent for user {}, alert {}", event.getUserId(), event.getAlertId());

        Map<String, Object> model = new HashMap<>();
        model.put("recipientName", "Traveler");
        model.put("propertyTitle", event.getTitle());
        model.put("newPrice", "$" + event.getCurrentPrice() + " / night");

        dispatcher.dispatch(
                event.getUserId(),
                NotificationCategory.ALERTS,
                "WATCHDOG_ALERT_" + event.getAlertType().name(),
                event.getTitle(),
                event.getDescription(),
                "{\"alertId\":\"" + event.getAlertId() + "\",\"propertyId\":\"" + event.getPropertyId() + "\"}",
                "price-drop-alert",
                model
        );
    }
}
