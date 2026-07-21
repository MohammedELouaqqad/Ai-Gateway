package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GuardrailsProperties.class)
public class GuardrailsConfig {
}
