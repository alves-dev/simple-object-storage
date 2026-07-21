package com.alves_dev.sos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "orphan_storage_files")
public class OrphanStorageFile {
    @Id
    private String id;
    @Indexed(unique = true)
    private String relativePath;
    private String bucketName;
    private Instant detectedAt;
    private Instant lastSeenAt;
    private Long fileSize;
    private OrphanStatus status;

    public OrphanStorageFile() {
    }

    public String getRelativePath() {
        return relativePath;
    }

    public OrphanStatus getStatus() {
        return status;
    }
}
