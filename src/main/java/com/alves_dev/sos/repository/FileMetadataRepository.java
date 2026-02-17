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

    boolean existsByFileId(String fileId);

    Page<FileMetadata> findByBucket(String bucket, Pageable pageable);

    void deleteByFileId(String fileId);
}