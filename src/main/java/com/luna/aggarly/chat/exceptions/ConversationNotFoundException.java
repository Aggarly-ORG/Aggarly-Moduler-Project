package com.luna.aggarly.chat.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ConversationNotFoundException extends AggarlyException {
    public ConversationNotFoundException(UUID id) {
        super("Conversation not found: " + id, HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND");
    }
}
