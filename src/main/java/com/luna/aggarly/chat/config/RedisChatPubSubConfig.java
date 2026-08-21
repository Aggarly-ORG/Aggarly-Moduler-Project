package com.luna.aggarly.chat.config;

import com.luna.aggarly.chat.service.impl.RedisChatSubscriberImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@Profile("!test")
public class RedisChatPubSubConfig {

    public static final String CHAT_CHANNEL = "aggarly:chat:messages";

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic(CHAT_CHANNEL);
    }

    @Bean
    public MessageListenerAdapter chatMessageListenerAdapter(RedisChatSubscriberImpl subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisChatContainer(RedisConnectionFactory connectionFactory,
                                                            MessageListenerAdapter chatMessageListenerAdapter,
                                                            ChannelTopic chatTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageListenerAdapter, chatTopic);
        return container;
    }
}
