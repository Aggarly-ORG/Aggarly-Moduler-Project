package com.luna.aggarly.review.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ReviewCreatedEvent extends ApplicationEvent {

    private final UUID propertyId;
    private final int rating;

    public ReviewCreatedEvent(Object source, UUID propertyId, int rating) {
        super(source);
        this.propertyId = propertyId;
        this.rating = rating;
    }
}
