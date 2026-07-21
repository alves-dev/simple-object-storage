package com.alves_dev.sos.repository;

import com.alves_dev.sos.model.Bucket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BucketRepository extends MongoRepository<Bucket, String> {
    Optional<Bucket> findByName(String name);
    Page<Bucket> findAllByOrderByCreatedAtDesc(Pageable pageable);
    void deleteByName(String name);
}
