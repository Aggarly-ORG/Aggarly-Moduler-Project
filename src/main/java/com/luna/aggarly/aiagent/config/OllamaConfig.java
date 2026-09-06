package com.luna.aggarly.aiagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "aggarly.ai.provider", havingValue = "ollama")
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.model:nemotron-3-super:cloud}")
    private String chatModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.1}")
    private Double temperature;

    @Value("${spring.ai.ollama.chat.options.num-ctx:8192}")
    private Integer numCtx;

    @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}")
    private String embeddingModel;

    @Bean
    public OllamaApi ollamaApi() {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(30));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(180));

        org.springframework.web.client.RestClient.Builder restClientBuilder = org.springframework.web.client.RestClient.builder()
                .requestFactory(requestFactory);

        return new OllamaApi.Builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(chatModel)
                .temperature(temperature)
                .numCtx(numCtx)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel);
    }

    @Bean
    public EmbeddingModel embeddingModel(OllamaApi ollamaApi) {
        OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                .model(embeddingModel)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .build();
    }
}
