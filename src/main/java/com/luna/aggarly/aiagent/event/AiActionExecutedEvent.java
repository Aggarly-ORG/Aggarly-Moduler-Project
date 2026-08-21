package com.luna.aggarly.aiagent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class AiActionExecutedEvent extends ApplicationEvent {

    private final UUID conversationId;
    private final UUID userId;
    private final String toolName;
    private final boolean success;

    public AiActionExecutedEvent(Object source, UUID conversationId, UUID userId, String toolName, boolean success) {
        super(source);
        this.conversationId = conversationId;
        this.userId = userId;
        this.toolName = toolName;
        this.success = success;
    }
}
