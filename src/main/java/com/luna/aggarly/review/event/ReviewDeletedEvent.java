package com.luna.aggarly.review.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ReviewDeletedEvent extends ApplicationEvent {

    private final UUID propertyId;

    public ReviewDeletedEvent(Object source, UUID propertyId) {
        super(source);
        this.propertyId = propertyId;
    }
}
