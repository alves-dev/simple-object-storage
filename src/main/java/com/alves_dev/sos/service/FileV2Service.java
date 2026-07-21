package com.alves_dev.sos.service;

import com.alves_dev.sos.config.ServerConfig;
import com.alves_dev.sos.config.StorageConfig;
import com.alves_dev.sos.exception.AccessDeniedException;
import com.alves_dev.sos.exception.BucketDisabledException;
import com.alves_dev.sos.exception.DuplicateFilenameException;
import com.alves_dev.sos.exception.InvalidFilenameException;
import com.alves_dev.sos.exception.FileTooLargeException;
import com.alves_dev.sos.exception.StorageException;
import com.alves_dev.sos.exception.VersionConflictException;
import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.dto.v2.FileResponse;
import com.alves_dev.sos.repository.FileMetadataRepository;
import com.alves_dev.sos.security.AuthenticatedClient;
import com.alves_dev.sos.util.AccessKeyGenerator;
import com.alves_dev.sos.util.FileNameGenerator;
import com.alves_dev.sos.util.FilenameNormalizer;
import com.alves_dev.sos.util.HashUtils;
import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class FileV2Service {

    private static final Logger log = LoggerFactory.getLogger(FileV2Service.class);

    private final FileMetadataRepository repository;
    private final FileStorageService storage;
    private final BucketService bucketService;
    private final BucketAuthorizationService authorization;
    private final FilenameNormalizer filenameNormalizer;
    private final FileNameGenerator internalNameGenerator;
    private final AccessKeyGenerator accessKeyGenerator;
    private final FileContentCacheService cache;
    private final MongoTemplate mongoTemplate;
    private final StorageConfig storageConfig;
    private final ServerConfig serverConfig;

    public FileV2Service(FileMetadataRepository repository, FileStorageService storage,
                         BucketService bucketService, BucketAuthorizationService authorization,
                         FilenameNormalizer filenameNormalizer, FileNameGenerator internalNameGenerator,
                         AccessKeyGenerator accessKeyGenerator, FileContentCacheService cache,
                         MongoTemplate mongoTemplate, StorageConfig storageConfig, ServerConfig serverConfig) {
        this.repository = repository;
        this.storage = storage;
        this.bucketService = bucketService;
        this.authorization = authorization;
        this.filenameNormalizer = filenameNormalizer;
        this.internalNameGenerator = internalNameGenerator;
        this.accessKeyGenerator = accessKeyGenerator;
        this.cache = cache;
        this.mongoTemplate = mongoTemplate;
        this.storageConfig = storageConfig;
        this.serverConfig = serverConfig;
    }

    public UploadResult upload(MultipartFile file, String bucketInput, String requestedFilename,
                               boolean isPublic, Map<String, Object> metadata, boolean metadataProvided,
                               boolean forceReplace, boolean friendlyUrl, AuthenticatedClient client) {
        validateFile(file);
        Bucket bucket = bucketService.findOrCreate(bucketInput, client);
        String filename = validateFilename(StringUtils.hasText(requestedFilename)
                ? requestedFilename : filenameNormalizer.derive(file.getOriginalFilename()));
        String normalized = filenameNormalizer.normalize(filename);
        var existing = repository.findByBucketAndNormalizedFilename(bucket.getName(), normalized);
        if (existing.isPresent()) {
            if (!forceReplace) {
                authorization.requireUpload(client, bucket);
                throw new DuplicateFilenameException(filename);
            }
            if (!bucket.isEnabled()) {
                throw new BucketDisabledException(bucket.getName());
            }
            return new UploadResult(replace(file, existing.get(), bucket, isPublic, metadata,
                    metadataProvided, client), false);
        }
        authorization.requireUpload(client, bucket);
        return new UploadResult(create(file, bucket, filename, normalized, isPublic, metadata,
                friendlyUrl, client), true);
    }

    private FileResponse create(MultipartFile file, Bucket bucket, String filename, String normalized,
                                boolean isPublic, Map<String, Object> metadata, boolean friendlyUrl,
                                AuthenticatedClient client) {
        String temporary = storage.storeTemporary(file, bucket.getName());
        String storedName = internalNameGenerator.generate(filename);
        try {
            String hash = HashUtils.sha256(Path.of(temporary));
            String finalPath = storage.moveTemporaryToFinal(temporary, bucket.getName(), storedName);
            String accessKey = isPublic ? null : accessKeyGenerator.generate();
            FileMetadata created = FileMetadata.createV2(randomId(), bucket.getName(), file.getOriginalFilename(),
                    storedName, finalPath, contentType(file), file.getSize(), isPublic, accessKey, metadata,
                    filename, normalized, friendlyUrl, hash, client.clientId());
            try {
                repository.save(created);
            } catch (DuplicateKeyException exception) {
                storage.deleteIfExists(finalPath);
                throw new DuplicateFilenameException(filename);
            } catch (RuntimeException exception) {
                storage.deleteIfExists(finalPath);
                throw exception;
            }
            log.info("File upload filename={} bucket={} clientId={}", filename, bucket.getName(), client.clientId());
            return toResponse(created, accessKey);
        } catch (IOException exception) {
            storage.deleteIfExists(temporary);
            throw new StorageException("Failed to hash uploaded file", exception);
        } catch (RuntimeException exception) {
            storage.deleteIfExists(temporary);
            throw exception;
        }
    }

    private FileResponse replace(MultipartFile file, FileMetadata existing, Bucket bucket, boolean isPublic,
                                 Map<String, Object> metadata, boolean metadataProvided,
                                 AuthenticatedClient client) {
        authorization.requireOwnerOrAdmin(client, bucket);
        if (!existing.getIsPublic().equals(isPublic)) {
            throw new AccessDeniedException("Replacement cannot change file visibility");
        }
        String temporary = storage.storeTemporary(file, bucket.getName());
        String storedName = internalNameGenerator.generate(existing.getFilename());
        String newPath = null;
        boolean metadataCommitted = false;
        try {
            String hash = HashUtils.sha256(Path.of(temporary));
            newPath = storage.moveTemporaryToFinal(temporary, bucket.getName(), storedName);
            long previousVersion = existing.getVersion() == null ? 1L : existing.getVersion();
            Update update = new Update()
                    .set("storedFileName", storedName).set("filePath", newPath)
                    .set("contentHash", hash).set("fileSize", file.getSize())
                    .set("mimeType", contentType(file)).set("updatedAt", Instant.now())
                    .inc("version", 1);
            if (metadataProvided && metadata != null && !metadata.isEmpty()) {
                update.set("metadata", metadata);
            }
            UpdateResult result = mongoTemplate.updateFirst(Query.query(Criteria.where("fileId")
                    .is(existing.getFileId()).and("version").is(previousVersion)), update, FileMetadata.class);
            if (result.getModifiedCount() != 1) {
                storage.deleteIfExists(newPath);
                throw new VersionConflictException(existing.getFileId());
            }
            metadataCommitted = true;
            cache.invalidate(existing);
            try {
                storage.deleteIfExists(existing.getFilePath());
            } catch (RuntimeException exception) {
                log.error("Old physical file cleanup failed for fileId={}", existing.getFileId());
            }
            FileMetadata replaced = repository.findByFileId(existing.getFileId()).orElseThrow();
            log.info("File replacement filename={} bucket={} clientId={}",
                    existing.getFilename(), bucket.getName(), client.clientId());
            return toResponse(replaced, null);
        } catch (IOException exception) {
            storage.deleteIfExists(temporary);
            if (newPath != null) storage.deleteIfExists(newPath);
            throw new StorageException("Failed to hash replacement file", exception);
        } catch (RuntimeException exception) {
            storage.deleteIfExists(temporary);
            if (!metadataCommitted && newPath != null) {
                storage.deleteIfExists(newPath);
            }
            throw exception;
        }
    }

    public FileResponse toResponse(FileMetadata file, String accessKey) {
        String base = serverConfig.baseUrl() + "/files/" + file.getBucket() + "/";
        String url = base + file.getFileId();
        String friendly = Boolean.TRUE.equals(file.getFriendlyUrlEnabled()) ? base + file.getFilename() : null;
        String privateUrl = accessKey == null ? null : url + "?key=" + accessKey;
        return new FileResponse(file.getFileId(), file.getBucket(), file.getOriginalFileName(), file.getFilename(),
                url, friendly, file.getIsPublic(), accessKey, privateUrl, file.getFileSize(), file.getMimeType(),
                file.getContentHash(), file.getVersion(), file.getUploadedAt(), file.getUpdatedAt(),
                file.getMetadata(), file.getStorageStatus(), file.getLastDirectAccessDate(),
                file.getDirectAccessCount(), file.getRecentDirectAccessCount());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidFilenameException("File is required");
        if (file.getSize() > storageConfig.maxFileSize()) {
            throw new FileTooLargeException();
        }
    }

    private String validateFilename(String filename) {
        if (!StringUtils.hasText(filename) || !filename.matches(storageConfig.allowedFilenameRegex())) {
            throw new InvalidFilenameException(
                    "Filename must contain only alphanumeric characters, dots, hyphens and underscores");
        }
        return filename;
    }

    private String contentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType())
                ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record UploadResult(FileResponse response, boolean created) {
    }
}
