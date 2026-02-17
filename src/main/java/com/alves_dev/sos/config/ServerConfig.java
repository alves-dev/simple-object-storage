package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "server")
public record ServerConfig(
        String baseUrl,
        List<String> apiKeys
) {
}