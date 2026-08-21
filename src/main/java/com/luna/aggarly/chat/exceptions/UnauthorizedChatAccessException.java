package com.luna.aggarly.chat.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UnauthorizedChatAccessException extends AggarlyException {
    public UnauthorizedChatAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_CHAT_ACCESS");
    }
}
