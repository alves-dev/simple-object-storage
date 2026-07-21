package com.alves_dev.sos.service;

import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.StorageStatus;
import com.alves_dev.sos.repository.BucketRepository;
import com.alves_dev.sos.repository.ClientRepository;
import com.alves_dev.sos.repository.FileMetadataRepository;
import com.alves_dev.sos.util.FilenameNormalizer;
import com.alves_dev.sos.util.HashUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class V2MigrationService {

    private static final String LEGACY_OWNER = "developer-admin";

    private final FileMetadataRepository fileRepository;
    private final BucketRepository bucketRepository;
    private final ClientRepository clientRepository;
    private final ClientManagementService clientManagementService;
    private final FilenameNormalizer filenameNormalizer;
    private final MongoTemplate mongoTemplate;
    private final DatabaseIndexService databaseIndexService;
    private final FileStorageService fileStorageService;

    public V2MigrationService(FileMetadataRepository fileRepository,
                              BucketRepository bucketRepository,
                              ClientRepository clientRepository,
                              ClientManagementService clientManagementService,
                              FilenameNormalizer filenameNormalizer,
                              MongoTemplate mongoTemplate,
                              DatabaseIndexService databaseIndexService,
                              FileStorageService fileStorageService) {
        this.fileRepository = fileRepository;
        this.bucketRepository = bucketRepository;
        this.clientRepository = clientRepository;
        this.clientManagementService = clientManagementService;
        this.filenameNormalizer = filenameNormalizer;
        this.mongoTemplate = mongoTemplate;
        this.databaseIndexService = databaseIndexService;
        this.fileStorageService = fileStorageService;
    }

    public MigrationReport migrate(boolean dryRun) {
        List<FileMetadata> files = fileRepository.findAll(Sort.by("id"));
        Set<String> usedNames = new HashSet<>();
        files.stream()
                .filter(file -> file.getNormalizedFilename() != null)
                .forEach(file -> usedNames.add(key(Bucket.normalizeName(file.getBucket()),
                        file.getNormalizedFilename())));

        int bucketsToCreate = 0;
        int filenamesToDerive = 0;
        int duplicates = 0;
        int invalid = 0;
        int missing = 0;
        Set<String> seenBuckets = new HashSet<>();

        String createdAdminApiKey = null;
        if (!dryRun) {
            databaseIndexService.createClientIndexes();
            databaseIndexService.createBucketIndexes();
        }
        if (!dryRun && !clientRepository.existsByClientId(LEGACY_OWNER)) {
            createdAdminApiKey = clientManagementService
                    .create(LEGACY_OWNER, "Developer Admin", true).apiKey();
        }

        for (FileMetadata file : files) {
            try {
                String bucketName = Bucket.normalizeName(file.getBucket());
                if (bucketName == null || bucketName.isBlank()) {
                    invalid++;
                    continue;
                }
                if (seenBuckets.add(bucketName) && bucketRepository.findByName(bucketName).isEmpty()) {
                    bucketsToCreate++;
                    if (!dryRun) {
                        bucketRepository.save(new Bucket(bucketName, LEGACY_OWNER));
                    }
                }

                String filename = file.getFilename();
                if (filename == null || file.getNormalizedFilename() == null) {
                    if (filename == null) {
                        filenamesToDerive++;
                        filename = filenameNormalizer.derive(file.getOriginalFileName());
                    }
                    String normalized = filenameNormalizer.normalize(filename);
                    if (!usedNames.add(key(bucketName, normalized))) {
                        duplicates++;
                        filename = filenameNormalizer.disambiguate(filename, file.getFileId());
                        normalized = filenameNormalizer.normalize(filename);
                        int suffix = 2;
                        while (!usedNames.add(key(bucketName, normalized))) {
                            filename = filenameNormalizer.disambiguate(filename, file.getFileId()) + "_" + suffix++;
                            normalized = filenameNormalizer.normalize(filename);
                        }
                    }
                }

                Path physicalPath = file.getFilePath() == null
                        ? null : fileStorageService.resolveSafePath(file.getFilePath());
                boolean exists = physicalPath != null && Files.isRegularFile(physicalPath);
                if (!exists) {
                    missing++;
                }
                if (!dryRun) {
                    updateFile(file, bucketName, filename, physicalPath, exists);
                }
            } catch (RuntimeException exception) {
                invalid++;
            }
        }

        if (!dryRun) {
            createPostMigrationIndexes();
            databaseIndexService.createOperationalIndexes();
        }
        return new MigrationReport(files.size(), bucketsToCreate, filenamesToDerive, duplicates,
                invalid, missing, createdAdminApiKey);
    }

    private void updateFile(FileMetadata file, String bucketName, String filename, Path path, boolean exists) {
        Update update = new Update();
        if (!bucketName.equals(file.getBucket())) {
            update.set("bucket", bucketName);
        }
        if (file.getFilename() == null) {
            update.set("filename", filename);
        }
        if (file.getNormalizedFilename() == null) {
            update.set("normalizedFilename", filenameNormalizer.normalize(filename));
        }
        setIfMissing(update, file, "friendlyUrlEnabled", false);
        setIfMissing(update, file, "version", 1L);
        setIfMissing(update, file, "createdByClientId", LEGACY_OWNER);
        setIfMissing(update, file, "updatedAt", file.getUploadedAt() == null ? Instant.now() : file.getUploadedAt());
        setIfMissing(update, file, "directAccessCount", 0L);
        setIfMissing(update, file, "recentDirectAccessCount", 0L);
        setIfMissing(update, file, "storageStatus", exists ? StorageStatus.AVAILABLE : StorageStatus.MISSING);
        if (!exists) {
            setIfMissing(update, file, "missingDetectedAt", Instant.now());
        } else if (file.getContentHash() == null) {
            try {
                update.set("contentHash", HashUtils.sha256(path));
            } catch (java.io.IOException ignored) {
                update.set("storageStatus", StorageStatus.MISSING);
                setIfMissing(update, file, "missingDetectedAt", Instant.now());
            }
        }
        if (!update.getUpdateObject().isEmpty()) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(file.getId())), update, FileMetadata.class);
        }
    }

    private void setIfMissing(Update update, FileMetadata file, String field, Object value) {
        Query query = Query.query(Criteria.where("_id").is(file.getId()).and(field).exists(false));
        mongoTemplate.updateFirst(query, new Update().set(field, value), FileMetadata.class);
    }

    private void createPostMigrationIndexes() {
        IndexOperations fileIndexes = mongoTemplate.indexOps(FileMetadata.class);
        fileIndexes.createIndex(new Index().on("bucket", Sort.Direction.ASC)
                .on("normalizedFilename", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(
                        Criteria.where("bucket").exists(true).and("normalizedFilename").exists(true)))
                .named("bucket_normalized_filename_idx"));
        fileIndexes.createIndex(new Index().on("createdByClientId", Sort.Direction.ASC));
        fileIndexes.createIndex(new Index().on("lastDirectAccessDate", Sort.Direction.ASC));
        fileIndexes.createIndex(new Index().on("storageStatus", Sort.Direction.ASC)
                .on("lastStorageCheckAt", Sort.Direction.ASC));
        fileIndexes.createIndex(new Index().on("missingDetectedAt", Sort.Direction.ASC));
    }

    private String key(String bucket, String normalizedFilename) {
        return bucket + "\u0000" + normalizedFilename;
    }

    public record MigrationReport(int filesAnalyzed, int bucketsToCreate, int filenamesToDerive,
                                  int duplicatesFound, int invalidDocuments, int missingPhysicalFiles,
                                  String createdAdminApiKey) {
    }
}
