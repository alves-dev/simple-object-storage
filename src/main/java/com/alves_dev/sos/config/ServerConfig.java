package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "server")
public record ServerConfig(
        String baseUrl
) {
}
