package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage-integrity")
public record IntegrityConfig(boolean enabled, String cron, String zone, int batchSize,
                              int recheckDays, int workers, int maxItemsPerRun) {
}
