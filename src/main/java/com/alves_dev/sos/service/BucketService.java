package com.alves_dev.sos.service;

import com.alves_dev.sos.config.StorageConfig;
import com.alves_dev.sos.exception.BucketNotEmptyException;
import com.alves_dev.sos.exception.BucketNotFoundException;
import com.alves_dev.sos.exception.InvalidBucketException;
import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.BucketAction;
import com.alves_dev.sos.model.BucketPermission;
import com.alves_dev.sos.repository.BucketPermissionRepository;
import com.alves_dev.sos.repository.BucketRepository;
import com.alves_dev.sos.repository.FileMetadataRepository;
import com.alves_dev.sos.security.AuthenticatedClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumSet;

@Service
public class BucketService {

    private final BucketRepository bucketRepository;
    private final BucketPermissionRepository permissionRepository;
    private final FileMetadataRepository fileRepository;
    private final BucketAuthorizationService authorizationService;
    private final StorageConfig storageConfig;
    private final DatabaseIndexService databaseIndexService;

    public BucketService(BucketRepository bucketRepository,
                         BucketPermissionRepository permissionRepository,
                         FileMetadataRepository fileRepository,
                         BucketAuthorizationService authorizationService,
                         StorageConfig storageConfig,
                         DatabaseIndexService databaseIndexService) {
        this.bucketRepository = bucketRepository;
        this.permissionRepository = permissionRepository;
        this.fileRepository = fileRepository;
        this.authorizationService = authorizationService;
        this.storageConfig = storageConfig;
        this.databaseIndexService = databaseIndexService;
    }

    public String normalizeAndValidate(String name) {
        String normalized = Bucket.normalizeName(name);
        if (!StringUtils.hasText(normalized)
                || normalized.length() > storageConfig.maxBucketNameLength()
                || !normalized.matches(storageConfig.allowedBucketsRegex())) {
            throw new InvalidBucketException(
                    "Bucket name must contain only alphanumeric characters, hyphens and underscores");
        }
        return normalized;
    }

    public Bucket findOrCreate(String name, AuthenticatedClient client) {
        String normalized = normalizeAndValidate(name);
        return bucketRepository.findByName(normalized).orElseGet(() -> create(normalized, client.clientId()));
    }

    public Bucket findOrThrow(String name) {
        String normalized = normalizeAndValidate(name);
        return bucketRepository.findByName(normalized)
                .orElseThrow(() -> new BucketNotFoundException(normalized));
    }

    public Page<Bucket> list(Pageable pageable) {
        return bucketRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public long countFiles(Bucket bucket) {
        return fileRepository.countByBucket(bucket.getName());
    }

    public void delete(String name, AuthenticatedClient client) {
        Bucket bucket = findOrThrow(name);
        authorizationService.requireOwnerOrAdmin(client, bucket);
        if (fileRepository.countByBucket(bucket.getName()) > 0) {
            throw new BucketNotEmptyException(bucket.getName());
        }
        permissionRepository.deleteAllByBucketName(bucket.getName());
        bucketRepository.deleteByName(bucket.getName());
    }

    private Bucket create(String normalizedName, String creatorClientId) {
        databaseIndexService.createBucketIndexes();
        Bucket bucket;
        try {
            bucket = bucketRepository.save(new Bucket(normalizedName, creatorClientId));
        } catch (DuplicateKeyException exception) {
            return bucketRepository.findByName(normalizedName).orElseThrow(() -> exception);
        }

        try {
            permissionRepository.save(new BucketPermission(
                    creatorClientId, normalizedName, EnumSet.allOf(BucketAction.class)));
            return bucket;
        } catch (RuntimeException exception) {
            bucketRepository.deleteByName(normalizedName);
            throw exception;
        }
    }
}
