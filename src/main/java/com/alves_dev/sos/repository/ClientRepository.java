package com.alves_dev.sos.repository;

import com.alves_dev.sos.model.Client;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ClientRepository extends MongoRepository<Client, String> {
    Optional<Client> findByApiKeyHash(String apiKeyHash);
    boolean existsByClientId(String clientId);
}
