package com.luna.aggarly.chat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class MessageReadEvent extends ApplicationEvent {

    private final UUID conversationId;
    private final UUID userId;
    private final UUID lastReadMessageId;

    public MessageReadEvent(Object source,
                            UUID conversationId,
                            UUID userId,
                            UUID lastReadMessageId) {
        super(source);
        this.conversationId = conversationId;
        this.userId = userId;
        this.lastReadMessageId = lastReadMessageId;
    }
}
