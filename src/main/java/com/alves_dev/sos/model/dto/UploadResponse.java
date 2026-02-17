package com.alves_dev.sos.model.dto;

import java.time.Instant;

public record UploadResponse(
        String fileId,
        String bucket,
        String filename,
        String url,
        Boolean isPublic,
        String accessKey,
        String privateUrl,
        Long fileSize,
        String mimeType,
        Instant uploadedAt
) {
}