package com.alves_dev.sos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageConfig(
        String rootPath,
        Long maxFileSize,
        String allowedBucketsRegex,
        String allowedFilenameRegex,
        Integer maxBucketNameLength
) {

    public StorageConfig {
        if (maxFileSize == null) {
            maxFileSize = 52428800L;
        }
        if (allowedBucketsRegex == null) {
            allowedBucketsRegex = "^[a-zA-Z0-9_-]+$";
        }
        if (allowedFilenameRegex == null) {
            allowedFilenameRegex = "^[a-zA-Z0-9._-]+$";
        }
        if (maxBucketNameLength == null) {
            maxBucketNameLength = 50;
        }
    }
}
