package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "temporary-url")
public record TemporaryUrlConfig(String signingSecret, Duration minDuration, Duration maxDuration) {
}
