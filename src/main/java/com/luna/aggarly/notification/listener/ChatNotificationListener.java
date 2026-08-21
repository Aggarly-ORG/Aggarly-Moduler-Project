package com.luna.aggarly.notification.listener;

import com.luna.aggarly.chat.event.MessageSentEvent;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationListener {

    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        log.debug("Processing MessageSentEvent for message {}", event.getMessageId());

        String snippet = event.getContent().length() > 60
                ? event.getContent().substring(0, 57) + "..."
                : event.getContent();

        for (UUID recipientId : event.getRecipientIds()) {
            dispatcher.dispatch(
                    recipientId,
                    NotificationCategory.MESSAGES,
                    "NEW_CHAT_MESSAGE",
                    "New Message",
                    snippet,
                    "{\"conversationId\":\"" + event.getConversationId() + "\",\"messageId\":\"" + event.getMessageId() + "\"}",
                    null,
                    null
            );
        }
    }
}
