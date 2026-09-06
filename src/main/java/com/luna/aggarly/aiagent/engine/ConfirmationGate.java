package com.luna.aggarly.aiagent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.records.PendingConfirmationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConfirmationGate {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationGate.class);
    private static final Duration TTL = Duration.ofMinutes(15);

    private final Map<String, String> localStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    public String registerPendingConfirmation(
            UUID userId,
            String toolName,
            PendingConfirmationState state
    ) {
        String token = confirmationKey(userId, toolName);
        String json  = toJson(state);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(token, json, TTL);
                log.info("Registered Redis confirmation: user={}, tool={}, token={}", userId, toolName, token);
                return token;
            } catch (Exception ex) {
                log.warn("Redis write failed, falling back to local store", ex);
            }
        }

        localStore.put(token, json);
        log.info("Registered local confirmation: user={}, tool={}, token={}", userId, toolName, token);
        return token;
    }

    public Optional<PendingConfirmationState> retrieveAndConsume(String token) {
        String json = null;

        if (redisTemplate != null) {
            try {
                json = redisTemplate.opsForValue().get(token);
                if (json != null) redisTemplate.delete(token);
            } catch (Exception ex) {
                log.warn("Redis read failed, falling back to local store", ex);
            }
        }

        if (json == null) {
            json = localStore.remove(token);
        }

        if (json == null) {
            log.warn("No pending confirmation found for token={}", token);
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, PendingConfirmationState.class));
        } catch (Exception ex) {
            log.error("Failed to deserialize PendingConfirmationState for token={}", token, ex);
            return Optional.empty();
        }
    }

    public boolean exists(String token) {
        if (redisTemplate != null) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(token));
            } catch (Exception ex) {
                log.warn("Redis exists check failed, checking local store", ex);
            }
        }
        return localStore.containsKey(token);
    }

    public String confirmationKey(UUID userId, String toolName) {
        return "ai:confirmation:" + (userId != null ? userId : "guest") + ":" + toolName + ":" + UUID.randomUUID();
    }

    private String toJson(Object o) {
        if (o == null) return "null";
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception ex) {
            return o.toString();
        }
    }
}
