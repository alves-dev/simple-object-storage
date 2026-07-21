package com.alves_dev.sos.model.dto.v2;

import com.alves_dev.sos.model.StorageStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record FileResponse(
        String fileId, String bucket, String originalFilename, String filename,
        String url, String friendlyUrl, Boolean isPublic, String accessKey, String privateUrl,
        Long contentLength, String contentType, String contentHash, Long version,
        Instant createdAt, Instant updatedAt, Map<String, Object> metadata,
        StorageStatus storageStatus, LocalDate lastDirectAccessDate,
        Long directAccessCount, Long recentDirectAccessCount
) {
}
