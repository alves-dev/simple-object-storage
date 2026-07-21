package com.alves_dev.sos.service;

import com.alves_dev.sos.config.CacheConfig;
import com.alves_dev.sos.model.FileMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Optional;

@Service
public class FileContentCacheService {

    private static final Logger log = LoggerFactory.getLogger(FileContentCacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CacheConfig config;

    public FileContentCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, CacheConfig config) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public Optional<byte[]> get(FileMetadata metadata) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().get(key(metadata));
            if (value == null) {
                return Optional.empty();
            }
            CacheValue cached = objectMapper.readValue(value, CacheValue.class);
            return Optional.of(Base64.getDecoder().decode(cached.bytes()));
        } catch (Exception exception) {
            log.warn("Content cache read failed for fileId={}", metadata.getFileId());
            return Optional.empty();
        }
    }

    public void put(FileMetadata metadata, byte[] bytes) {
        if (!config.enabled() || bytes.length > config.maxFileSize().toBytes()) {
            return;
        }
        try {
            String encoded = objectMapper.writeValueAsString(new CacheValue(
                    Base64.getEncoder().encodeToString(bytes), metadata.getMimeType(),
                    metadata.getOriginalFileName(), metadata.getFileSize(), etag(metadata)));
            redis.opsForValue().set(key(metadata), encoded, config.ttl());
        } catch (Exception exception) {
            log.warn("Content cache write failed for fileId={}", metadata.getFileId());
        }
    }

    public void invalidate(FileMetadata metadata) {
        if (!config.enabled()) {
            return;
        }
        try {
            redis.delete(key(metadata));
        } catch (RuntimeException exception) {
            log.warn("Content cache invalidation failed for fileId={}", metadata.getFileId());
        }
    }

    public String etag(FileMetadata metadata) {
        String value = metadata.getContentHash() != null
                ? metadata.getContentHash()
                : "v" + (metadata.getVersion() == null ? 1 : metadata.getVersion());
        return "\"" + value + "\"";
    }

    private String key(FileMetadata metadata) {
        long version = metadata.getVersion() == null ? 1L : metadata.getVersion();
        return "sos:file-content:" + metadata.getFileId() + ":" + version;
    }

    private record CacheValue(String bytes, String contentType, String originalFilename,
                              Long contentLength, String etag) {
    }
}
