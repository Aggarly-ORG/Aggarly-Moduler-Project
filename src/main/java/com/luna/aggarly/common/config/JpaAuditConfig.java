package com.luna.aggarly.common.config;

import com.luna.aggarly.common.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditConfig {
    @Bean
    public AuditorAware<UUID> auditorProvider(){
        return () -> {
            UUID id = SecurityUtils.getCurrentUserId();
            return Optional.of(Objects.requireNonNullElseGet(id,
                    () -> new UUID(0, 0)));
        };
    }
}
