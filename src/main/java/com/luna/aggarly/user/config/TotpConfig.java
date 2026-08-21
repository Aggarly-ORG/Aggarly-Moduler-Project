package com.luna.aggarly.user.config;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TotpConfig {

    @Bean
    public GoogleAuthenticator googleAuthenticator() {
        GoogleAuthenticatorConfig config =
                new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setCodeDigits(6)
                        .setTimeStepSizeInMillis(30_000)
                        .setWindowSize(3)
                        .build();

        return new GoogleAuthenticator(config);
    }
}