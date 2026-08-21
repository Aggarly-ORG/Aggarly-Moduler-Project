package com.luna.aggarly.chat.event;

import com.luna.aggarly.chat.entity.enums.MessageType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

@Getter
public class MessageSentEvent extends ApplicationEvent {

    private final UUID messageId;
    private final UUID conversationId;
    private final UUID senderId;
    private final List<UUID> recipientIds;
    private final String content;
    private final MessageType messageType;

    public MessageSentEvent(Object source,
                            UUID messageId,
                            UUID conversationId,
                            UUID senderId,
                            List<UUID> recipientIds,
                            String content,
                            MessageType messageType) {
        super(source);
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.recipientIds = recipientIds;
        this.content = content;
        this.messageType = messageType;
    }
}
