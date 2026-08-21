package com.luna.aggarly.aiagent.config;

import com.luna.aggarly.aiagent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class ToolRegistryConfig {

    @Bean
    public Map<String, Tool<?, ?>> toolRegistry(List<Tool<?, ?>> allTools) {
        return allTools.stream().collect(Collectors.toMap(Tool::name, t -> t));
    }
}
