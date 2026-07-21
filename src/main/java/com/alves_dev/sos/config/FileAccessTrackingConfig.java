package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "file-access")
public record FileAccessTrackingConfig(Duration flushInterval, int recentWindowDays) {
}
