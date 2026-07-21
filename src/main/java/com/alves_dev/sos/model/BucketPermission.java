package com.alves_dev.sos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = "bucket_permissions")
@CompoundIndex(name = "client_bucket_unique_idx", def = "{'clientId': 1, 'bucketName': 1}", unique = true)
public class BucketPermission {

    @Id
    private String id;
    private String clientId;

    @Indexed
    private String bucketName;

    private Set<BucketAction> actions;
    private Instant createdAt;
    private Instant updatedAt;

    public BucketPermission(String clientId, String bucketName, Set<BucketAction> actions) {
        this.clientId = Client.normalizeClientId(clientId);
        this.bucketName = Bucket.normalizeName(bucketName);
        this.actions = Set.copyOf(actions);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public BucketPermission() {
    }

    public String getClientId() {
        return clientId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public Set<BucketAction> getActions() {
        return actions;
    }
}
