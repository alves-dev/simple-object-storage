package com.alves_dev.sos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
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

    // Getters
    public String getFileId() {
        return fileId;
    }

    public String getBucket() {
        return bucket;
    }

    public String getOriginalFileName() {
        return originalFileName;
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
}
