package com.luna.aggarly.aiagent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class AiMemoryUpdatedEvent extends ApplicationEvent {

    private final UUID userId;
    private final String memoryKey;
    private final String memoryValue;

    public AiMemoryUpdatedEvent(Object source, UUID userId, String memoryKey, String memoryValue) {
        super(source);
        this.userId = userId;
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
    }
}
