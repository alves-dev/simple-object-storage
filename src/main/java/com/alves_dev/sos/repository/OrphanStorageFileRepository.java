package com.alves_dev.sos.repository;

import com.alves_dev.sos.model.OrphanStorageFile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrphanStorageFileRepository extends MongoRepository<OrphanStorageFile, String> {
}
