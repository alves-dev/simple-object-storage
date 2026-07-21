package com.alves_dev.sos.repository;

import com.alves_dev.sos.model.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {

    Optional<FileMetadata> findByFileId(String fileId);
    Optional<FileMetadata> findByBucketAndFileId(String bucket, String fileId);
    Optional<FileMetadata> findByBucketAndNormalizedFilenameAndFriendlyUrlEnabledTrue(
            String bucket, String normalizedFilename);
    Optional<FileMetadata> findByBucketAndNormalizedFilename(String bucket, String normalizedFilename);
    Optional<FileMetadata> findByBucketAndStoredFileName(String bucket, String storedFileName);

    boolean existsByFileId(String fileId);

    Page<FileMetadata> findByBucket(String bucket, Pageable pageable);
    Page<FileMetadata> findByBucketOrderByUploadedAtDesc(String bucket, Pageable pageable);

    long countByBucket(String bucket);

    void deleteByFileId(String fileId);
}
