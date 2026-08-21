package com.luna.aggarly.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.chat.config.RedisChatPubSubConfig;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.service.RedisChatPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisChatPublisherImpl implements RedisChatPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishMessage(UUID conversationId, MessageResponse messageResponse) {
        try {
            String json = objectMapper.writeValueAsString(messageResponse);
            stringRedisTemplate.convertAndSend(RedisChatPubSubConfig.CHAT_CHANNEL, json);
            log.debug("Published chat message {} to Redis topic {}", messageResponse.id(), RedisChatPubSubConfig.CHAT_CHANNEL);
        } catch (Exception e) {
            log.warn("Redis pub/sub unavailable, falling back to direct local WebSocket dispatch: {}", e.getMessage());
            String destination = "/topic/conversation." + conversationId;
            messagingTemplate.convertAndSend(destination, messageResponse);
        }
    }
}
