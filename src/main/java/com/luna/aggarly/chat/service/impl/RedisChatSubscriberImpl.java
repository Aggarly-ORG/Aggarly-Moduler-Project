package com.luna.aggarly.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisChatSubscriberImpl implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            MessageResponse response = objectMapper.readValue(json, MessageResponse.class);

            String destination = "/topic/conversation." + response.conversationId();
            messagingTemplate.convertAndSend(destination, response);
            log.debug("Dispatched message from Redis to WebSocket STOMP: {}", destination);
        } catch (Exception e) {
            log.error("Failed to process message from Redis subscriber", e);
        }
    }
}
