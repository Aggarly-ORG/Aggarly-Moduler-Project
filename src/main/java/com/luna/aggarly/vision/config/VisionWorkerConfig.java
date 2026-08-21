package com.luna.aggarly.vision.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class VisionWorkerConfig {

    @Value("${aggarly.vision.worker.max-concurrent:3}")
    private int maxConcurrent = 3;

    @Bean(name = "visionWorkerExecutor")
    public ThreadPoolTaskExecutor visionWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("vision-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
