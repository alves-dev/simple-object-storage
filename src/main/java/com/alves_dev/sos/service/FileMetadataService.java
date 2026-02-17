package com.alves_dev.sos.service;

import com.alves_dev.sos.exception.FileNotFoundException;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.repository.FileMetadataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class FileMetadataService {

    private final FileMetadataRepository repository;

    public FileMetadataService(FileMetadataRepository repository) {
        this.repository = repository;
    }

    public FileMetadata save(FileMetadata metadata) {
        return repository.save(metadata);
    }

    public FileMetadata findByFileIdOrThrow(String fileId) {
        return repository.findByFileId(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    public Page<FileMetadata> findByBucket(String bucket, Pageable pageable) {
        return repository.findByBucket(bucket, pageable);
    }

    public void deleteByFileId(String fileId) {
        if (!repository.existsByFileId(fileId)) {
            throw new FileNotFoundException(fileId);
        }
        repository.deleteByFileId(fileId);
    }
}