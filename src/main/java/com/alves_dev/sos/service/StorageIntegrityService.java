package com.alves_dev.sos.service;

import com.alves_dev.sos.config.IntegrityConfig;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.OrphanStatus;
import com.alves_dev.sos.model.OrphanStorageFile;
import com.alves_dev.sos.model.StorageStatus;
import com.alves_dev.sos.repository.FileMetadataRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StorageIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(StorageIntegrityService.class);
    private final IntegrityConfig config;
    private final FileStorageService storage;
    private final FileMetadataRepository repository;
    private final FileContentCacheService cache;
    private final MongoTemplate mongoTemplate;
    private final DatabaseIndexService indexService;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService workers;

    public StorageIntegrityService(IntegrityConfig config, FileStorageService storage,
                                   FileMetadataRepository repository, FileContentCacheService cache,
                                   MongoTemplate mongoTemplate, DatabaseIndexService indexService) {
        this.config = config;
        this.storage = storage;
        this.repository = repository;
        this.cache = cache;
        this.mongoTemplate = mongoTemplate;
        this.indexService = indexService;
        this.workers = Executors.newFixedThreadPool(Math.max(1, config.workers()));
    }

    @Scheduled(cron = "${storage-integrity.cron:0 0 3 * * *}",
            zone = "${storage-integrity.zone:America/Sao_Paulo}")
    public void scheduledCheck() {
        if (!config.enabled() || !running.compareAndSet(false, true)) return;
        try {
            indexService.createOperationalIndexes();
            int checkedMetadata = checkMetadataToFilesystem();
            checkFilesystemToMetadata(Math.max(0, config.maxItemsPerRun() - checkedMetadata));
        } finally {
            running.set(false);
        }
    }

    int checkMetadataToFilesystem() {
        Instant cutoff = Instant.now().minus(config.recheckDays(), ChronoUnit.DAYS);
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("lastStorageCheckAt").exists(false),
                Criteria.where("lastStorageCheckAt").lt(cutoff)))
                .with(Sort.by("lastStorageCheckAt")).limit(config.maxItemsPerRun());
        List<FileMetadata> files = mongoTemplate.find(query, FileMetadata.class);
        for (int start = 0; start < files.size(); start += Math.max(1, config.batchSize())) {
            List<FileMetadata> batch = files.subList(start, Math.min(files.size(), start + config.batchSize()));
            var futures = batch.stream().map(file -> workers.submit(() -> checkOne(file))).toList();
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception exception) {
                    log.warn("Storage integrity worker failed");
                }
            });
        }
        return files.size();
    }

    private void checkOne(FileMetadata file) {
        boolean exists;
        try {
            Path path = storage.resolveSafePath(file.getFilePath());
            exists = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path);
        } catch (RuntimeException exception) {
            exists = false;
        }
        Update update = new Update().set("lastStorageCheckAt", Instant.now());
        if (exists) {
            update.set("storageStatus", StorageStatus.AVAILABLE).unset("missingDetectedAt");
        } else {
            update.set("storageStatus", StorageStatus.MISSING);
            if (file.getMissingDetectedAt() == null) update.set("missingDetectedAt", Instant.now());
            cache.invalidate(file);
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("fileId").is(file.getFileId())), update,
                FileMetadata.class);
    }

    void checkFilesystemToMetadata(int remainingItems) {
        if (remainingItems <= 0) return;
        Path root = storage.root();
        if (!Files.isDirectory(root)) return;
        Set<String> seen = new HashSet<>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .limit(remainingItems)
                    .forEach(path -> inspectPhysical(root, path, seen));
        } catch (IOException exception) {
            log.warn("Could not scan storage root for orphan files");
        }
        resolveAbsentOrphans(root, seen);
    }

    private void inspectPhysical(Path root, Path path, Set<String> seen) {
        Path relative = root.relativize(path);
        if (relative.getNameCount() < 2 || path.getFileName().toString().startsWith(".sos-tmp-")) return;
        String bucket = relative.getName(0).toString();
        String storedName = path.getFileName().toString();
        if (repository.findByBucketAndStoredFileName(bucket, storedName).isPresent()) return;
        String relativePath = relative.toString();
        seen.add(relativePath);
        Query query = Query.query(Criteria.where("relativePath").is(relativePath));
        Update update = new Update().set("bucketName", bucket).set("lastSeenAt", Instant.now())
                .set("fileSize", safeSize(path)).set("status", OrphanStatus.DETECTED)
                .setOnInsert("detectedAt", Instant.now());
        mongoTemplate.upsert(query, update, OrphanStorageFile.class);
    }

    private void resolveAbsentOrphans(Path root, Set<String> seen) {
        for (OrphanStorageFile orphan : mongoTemplate.findAll(OrphanStorageFile.class)) {
            if (orphan.getStatus() == OrphanStatus.IGNORED || seen.contains(orphan.getRelativePath())) continue;
            Path path = storage.resolveSafePath(root.resolve(orphan.getRelativePath()).toString());
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                mongoTemplate.updateFirst(Query.query(Criteria.where("relativePath").is(orphan.getRelativePath())),
                        new Update().set("status", OrphanStatus.RESOLVED), OrphanStorageFile.class);
            }
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return -1L;
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
    }
}
