package com.alves_dev.sos.model.dto;

import java.util.Map;

public record UploadRequest(
        String bucket,
        String filename,
        Boolean isPublic,
        Map<String, Object> metadata
) {

    public UploadRequest {
        if (isPublic == null) {
            isPublic = true;
        }
    }
}
