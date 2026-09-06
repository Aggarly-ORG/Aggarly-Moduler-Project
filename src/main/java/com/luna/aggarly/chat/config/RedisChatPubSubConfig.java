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
    public static final String ACTIVITY_CHANNEL = "aggarly:chat:activities";

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic(CHAT_CHANNEL);
    }

    @Bean
    public ChannelTopic activityTopic() {
        return new ChannelTopic(ACTIVITY_CHANNEL);
    }

    @Bean
    public MessageListenerAdapter chatMessageListenerAdapter(RedisChatSubscriberImpl subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public MessageListenerAdapter activityMessageListenerAdapter(com.luna.aggarly.chat.service.impl.RedisActivitySubscriberImpl subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisChatContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter chatMessageListenerAdapter,
            ChannelTopic chatTopic,
            MessageListenerAdapter activityMessageListenerAdapter,
            ChannelTopic activityTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageListenerAdapter, chatTopic);
        container.addMessageListener(activityMessageListenerAdapter, activityTopic);
        return container;
    }
}
