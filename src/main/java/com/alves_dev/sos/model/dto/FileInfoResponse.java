package com.alves_dev.sos.model.dto;

import java.time.Instant;
import java.util.Map;

public record FileInfoResponse(
        String fileId,
        String bucket,
        String filename,
        String mimeType,
        Long fileSize,
        Boolean isPublic,
        Instant uploadedAt,
        Map<String, Object> metadata
) {
}