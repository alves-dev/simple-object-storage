package com.alves_dev.sos.service;

import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.BucketPermission;
import com.alves_dev.sos.model.Client;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.OrphanStorageFile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

@Service
public class DatabaseIndexService {

    private final MongoTemplate mongoTemplate;

    public DatabaseIndexService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void createClientIndexes() {
        mongoTemplate.indexOps(Client.class)
                .createIndex(new Index().on("clientId", Sort.Direction.ASC).unique().named("client_id_unique_idx"));
        mongoTemplate.indexOps(Client.class)
                .createIndex(new Index().on("apiKeyHash", Sort.Direction.ASC).unique().named("api_key_hash_unique_idx"));
    }

    public void createBucketIndexes() {
        mongoTemplate.indexOps(Bucket.class)
                .createIndex(new Index().on("name", Sort.Direction.ASC).unique().named("bucket_name_unique_idx"));
        mongoTemplate.indexOps(BucketPermission.class)
                .createIndex(new Index().on("clientId", Sort.Direction.ASC)
                        .on("bucketName", Sort.Direction.ASC).unique().named("client_bucket_unique_idx"));
        mongoTemplate.indexOps(BucketPermission.class)
                .createIndex(new Index().on("bucketName", Sort.Direction.ASC).named("permission_bucket_idx"));
    }

    public void createOperationalIndexes() {
        mongoTemplate.indexOps(FileMetadata.class).createIndex(new Index()
                .on("bucket", Sort.Direction.ASC).on("isPublic", Sort.Direction.ASC)
                .on("storageStatus", Sort.Direction.ASC).on("mimeType", Sort.Direction.ASC)
                .named("random_image_filter_idx"));
        mongoTemplate.indexOps(OrphanStorageFile.class).createIndex(new Index()
                .on("relativePath", Sort.Direction.ASC).unique().named("orphan_relative_path_unique_idx"));
    }
}
