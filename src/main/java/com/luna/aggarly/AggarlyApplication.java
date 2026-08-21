package com.luna.aggarly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
		"org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration",
		"org.springframework.ai.autoconfigure.chat.client.ChatClientAutoConfiguration",
		"org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration"
})
@EnableAsync
@EnableScheduling
public class AggarlyApplication {

	public static void main(String[] args) {
		SpringApplication.run(AggarlyApplication.class, args);
	}

}
