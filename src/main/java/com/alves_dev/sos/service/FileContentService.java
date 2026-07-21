package com.alves_dev.sos.service;

import com.alves_dev.sos.exception.ContentNotFoundException;
import com.alves_dev.sos.exception.FileNotFoundException;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.StorageStatus;
import com.alves_dev.sos.repository.FileMetadataRepository;
import com.alves_dev.sos.util.FilenameNormalizer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FileContentService {

    private final FileMetadataRepository repository;
    private final FileStorageService storage;
    private final FileContentCacheService cache;
    private final FilenameNormalizer filenameNormalizer;
    private final MongoTemplate mongoTemplate;

    public FileContentService(FileMetadataRepository repository, FileStorageService storage,
                              FileContentCacheService cache, FilenameNormalizer filenameNormalizer,
                              MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.storage = storage;
        this.cache = cache;
        this.filenameNormalizer = filenameNormalizer;
        this.mongoTemplate = mongoTemplate;
    }

    public FileMetadata resolveLegacy(String fileId) {
        return repository.findByFileId(fileId).orElseThrow(() -> new FileNotFoundException(fileId));
    }

    public FileMetadata resolveV2(String bucket, String value) {
        String normalizedBucket = com.alves_dev.sos.model.Bucket.normalizeName(bucket);
        return repository.findByBucketAndFileId(normalizedBucket, value)
                .or(() -> repository.findByBucketAndNormalizedFilenameAndFriendlyUrlEnabledTrue(
                        normalizedBucket, filenameNormalizer.normalize(value)))
                .orElseThrow(() -> new FileNotFoundException(value));
    }

    public byte[] load(FileMetadata file) {
        if (!storage.exists(file.getFilePath())) {
            markMissing(file);
            cache.invalidate(file);
            throw new ContentNotFoundException(file.getFileId());
        }
        byte[] bytes = cache.get(file).orElseGet(() -> {
            byte[] loaded = storage.readAllBytes(file.getFilePath());
            cache.put(file, loaded);
            return loaded;
        });
        if (file.getStorageStatus() == StorageStatus.MISSING) {
            markAvailable(file);
        }
        return bytes;
    }

    private void markMissing(FileMetadata file) {
        Update update = new Update().set("storageStatus", StorageStatus.MISSING)
                .set("lastStorageCheckAt", Instant.now());
        if (file.getMissingDetectedAt() == null) update.set("missingDetectedAt", Instant.now());
        mongoTemplate.updateFirst(Query.query(Criteria.where("fileId").is(file.getFileId())), update,
                FileMetadata.class);
    }

    private void markAvailable(FileMetadata file) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("fileId").is(file.getFileId())),
                new Update().set("storageStatus", StorageStatus.AVAILABLE)
                        .set("lastStorageCheckAt", Instant.now()).unset("missingDetectedAt"),
                FileMetadata.class);
    }
}
