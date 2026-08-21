package com.luna.aggarly.notification.event;

import com.luna.aggarly.notification.entity.enums.AlertWatchType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class AiAlertTriggeredEvent extends ApplicationEvent {

    private final UUID alertId;
    private final UUID userId;
    private final UUID propertyId;
    private final AlertWatchType alertType;
    private final String title;
    private final String description;
    private final BigDecimal currentPrice;

    public AiAlertTriggeredEvent(Object source,
                                 UUID alertId,
                                 UUID userId,
                                 UUID propertyId,
                                 AlertWatchType alertType,
                                 String title,
                                 String description,
                                 BigDecimal currentPrice) {
        super(source);
        this.alertId = alertId;
        this.userId = userId;
        this.propertyId = propertyId;
        this.alertType = alertType;
        this.title = title;
        this.description = description;
        this.currentPrice = currentPrice;
    }
}
