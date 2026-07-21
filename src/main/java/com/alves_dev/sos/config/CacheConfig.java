package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "content-cache")
public record CacheConfig(boolean enabled, String host, int port, DataSize maxFileSize,
                          Duration ttl, Duration timeout) {
}
