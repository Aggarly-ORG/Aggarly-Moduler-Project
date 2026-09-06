package com.luna.aggarly.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.activity.AgentActivityEvent;
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
public class RedisActivitySubscriberImpl implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            AgentActivityEvent event = objectMapper.readValue(json, AgentActivityEvent.class);

            if (event.conversationId() != null) {
                messagingTemplate.convertAndSend("/topic/conversation." + event.conversationId(), event);
            } else {
                messagingTemplate.convertAndSend("/topic/conversation.00000000-0000-0000-0000-000000000000", event);
            }
            log.debug("Dispatched AI activity from Redis to WebSocket STOMP for conv {}", event.conversationId());
        } catch (Exception e) {
            log.error("Failed to process AI activity from Redis subscriber", e);
        }
    }
}
