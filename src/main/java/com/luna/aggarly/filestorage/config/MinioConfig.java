package com.luna.aggarly.filestorage.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {
    @Value("${app.minio.endpoint}")
    private String endpoint;
    @Value("${app.minio.access-key}")
    private String accessKey;
    @Value("${app.minio.secret-key}")
    private String secretKey;
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey,secretKey)
                .build();
    }
}
