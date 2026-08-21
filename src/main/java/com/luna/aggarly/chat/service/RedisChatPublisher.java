package com.luna.aggarly.chat.service;

import com.luna.aggarly.chat.dto.response.MessageResponse;

import java.util.UUID;

public interface RedisChatPublisher {

    void publishMessage(UUID conversationId, MessageResponse messageResponse);
}
