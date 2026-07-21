package com.alves_dev.sos.model.dto.v2;

import java.time.Instant;

public record BucketResponse(
        String name,
        String createdByClientId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Long fileCount
) {
}
