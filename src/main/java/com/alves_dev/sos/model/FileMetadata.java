package com.alves_dev.sos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Document(collection = "files")
@CompoundIndexes({
        @CompoundIndex(name = "bucket_storedFileName_idx", def = "{'bucket': 1, 'storedFileName': 1}", unique = true)
})
public class FileMetadata {

    @Id
    private String id;

    @Indexed(unique = true)
    private String fileId;

    private String bucket;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private String mimeType;
    private Long fileSize;
    private Boolean isPublic;

    @Indexed(unique = true, sparse = true)
    private String accessKey;

    private Instant uploadedAt;
    private Map<String, Object> metadata;
    private String filename;
    private String normalizedFilename;
    private Boolean friendlyUrlEnabled;
    private String contentHash;
    private Long version;
    private String createdByClientId;
    private Instant updatedAt;
    private LocalDate lastDirectAccessDate;
    private Long directAccessCount;
    private Long recentDirectAccessCount;
    private LocalDate recentAccessWindowStart;
    private StorageStatus storageStatus;
    private Instant lastStorageCheckAt;
    private Instant missingDetectedAt;

    public FileMetadata(String fileId, String bucket, String originalFileName, String storedFileName, String filePath,
                        String mimeType, Long fileSize, Boolean isPublic, String accessKey, Map<String, Object> metadata) {
        this.fileId = fileId;
        this.bucket = bucket;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.filePath = filePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.isPublic = isPublic;
        this.accessKey = accessKey;
        this.uploadedAt = Instant.now();
        this.metadata = metadata;
    }

    public FileMetadata() {
    }

    public static FileMetadata createV2(String fileId, String bucket, String originalFileName,
                                        String storedFileName, String filePath, String mimeType, Long fileSize,
                                        Boolean isPublic, String accessKey, Map<String, Object> metadata,
                                        String filename, String normalizedFilename, boolean friendlyUrlEnabled,
                                        String contentHash, String createdByClientId) {
        FileMetadata file = new FileMetadata(fileId, bucket, originalFileName, storedFileName, filePath,
                mimeType, fileSize, isPublic, accessKey, metadata);
        file.filename = filename;
        file.normalizedFilename = normalizedFilename;
        file.friendlyUrlEnabled = friendlyUrlEnabled;
        file.contentHash = contentHash;
        file.version = 1L;
        file.createdByClientId = createdByClientId;
        file.updatedAt = file.uploadedAt;
        file.directAccessCount = 0L;
        file.recentDirectAccessCount = 0L;
        file.storageStatus = StorageStatus.AVAILABLE;
        return file;
    }

    // Getters
    public String getFileId() {
        return fileId;
    }

    public String getId() {
        return id;
    }

    public String getBucket() {
        return bucket;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getFilename() {
        return filename;
    }

    public String getNormalizedFilename() {
        return normalizedFilename;
    }

    public Long getVersion() {
        return version;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Boolean getFriendlyUrlEnabled() {
        return friendlyUrlEnabled;
    }

    public String getCreatedByClientId() {
        return createdByClientId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public LocalDate getLastDirectAccessDate() {
        return lastDirectAccessDate;
    }

    public Long getDirectAccessCount() {
        return directAccessCount;
    }

    public Long getRecentDirectAccessCount() {
        return recentDirectAccessCount;
    }

    public LocalDate getRecentAccessWindowStart() {
        return recentAccessWindowStart;
    }

    public StorageStatus getStorageStatus() {
        return storageStatus;
    }

    public Instant getLastStorageCheckAt() {
        return lastStorageCheckAt;
    }

    public Instant getMissingDetectedAt() {
        return missingDetectedAt;
    }
}
