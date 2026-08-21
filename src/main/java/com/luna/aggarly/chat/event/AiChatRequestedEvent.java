package com.luna.aggarly.chat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class AiChatRequestedEvent extends ApplicationEvent {

    private final UUID conversationId;
    private final UUID userId;
    private final String content;

    public AiChatRequestedEvent(Object source,
                                UUID conversationId,
                                UUID userId,
                                String content) {
        super(source);
        this.conversationId = conversationId;
        this.userId = userId;
        this.content = content;
    }
}
