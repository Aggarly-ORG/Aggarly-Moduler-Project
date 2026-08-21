package com.luna.aggarly.chat.dto.request;

import com.luna.aggarly.chat.entity.enums.MessageType;
import jakarta.validation.constraints.NotBlank;

public record SendRestMessageRequest(
        @NotBlank(message = "Content cannot be blank")
        String content,

        MessageType messageType,

        String metadataJson
) {}
