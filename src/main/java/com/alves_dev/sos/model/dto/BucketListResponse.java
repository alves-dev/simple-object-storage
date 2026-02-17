package com.alves_dev.sos.model.dto;

import java.time.Instant;
import java.util.List;

public record BucketListResponse(
        String bucket,
        List<FileEntry> files,
        Pagination pagination
) {

    public record FileEntry(
            String fileId,
            String filename,
            Long fileSize,
            Boolean isPublic,
            Instant uploadedAt
    ) {
    }

    public record Pagination(
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}