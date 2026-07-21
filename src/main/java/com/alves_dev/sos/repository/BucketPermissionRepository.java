package com.alves_dev.sos.repository;

import com.alves_dev.sos.model.BucketPermission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BucketPermissionRepository extends MongoRepository<BucketPermission, String> {
    Optional<BucketPermission> findByClientIdAndBucketName(String clientId, String bucketName);
    void deleteAllByBucketName(String bucketName);
}
