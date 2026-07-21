package com.alves_dev.sos.service;

import com.alves_dev.sos.model.Client;
import com.alves_dev.sos.repository.ClientRepository;
import com.alves_dev.sos.util.ApiKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientManagementService {

    private static final String CLIENT_ID_PATTERN = "^[a-z0-9._-]+$";

    private final ClientRepository clientRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final DatabaseIndexService databaseIndexService;

    public ClientManagementService(ClientRepository clientRepository,
                                   ApiKeyGenerator apiKeyGenerator,
                                   DatabaseIndexService databaseIndexService) {
        this.clientRepository = clientRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.databaseIndexService = databaseIndexService;
    }

    public CreatedClient create(String clientId, String name, boolean admin) {
        databaseIndexService.createClientIndexes();
        String normalized = Client.normalizeClientId(clientId);
        if (!StringUtils.hasText(normalized) || !normalized.matches(CLIENT_ID_PATTERN)) {
            throw new IllegalArgumentException("clientId must match " + CLIENT_ID_PATTERN);
        }
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Client name is required");
        }
        if (clientRepository.existsByClientId(normalized)) {
            throw new IllegalArgumentException("Client '" + normalized + "' already exists");
        }
        String apiKey = apiKeyGenerator.generate();
        clientRepository.save(new Client(normalized, name, apiKeyGenerator.hash(apiKey), admin));
        return new CreatedClient(normalized, apiKey);
    }

    public record CreatedClient(String clientId, String apiKey) {
    }
}
