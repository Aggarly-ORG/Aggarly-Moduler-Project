package com.luna.aggarly.pricing.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class PricingRuleChangedEvent extends ApplicationEvent {
    private final UUID propertyId;

    public PricingRuleChangedEvent(Object source, UUID propertyId) {
        super(source);
        this.propertyId = propertyId;
    }
}
