package com.luna.aggarly.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory simple broker for broadcast (/topic) and user direct (/queue)
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for messages destined for methods annotated with @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix for user-specific queues
        registry.setUserDestinationPrefix("/user");
    }
}
