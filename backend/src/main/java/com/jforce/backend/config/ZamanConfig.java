package com.jforce.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ZamanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}