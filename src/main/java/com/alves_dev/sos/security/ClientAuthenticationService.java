package com.alves_dev.sos.security;

import com.alves_dev.sos.exception.ClientDisabledException;
import com.alves_dev.sos.exception.InvalidApiKeyException;
import com.alves_dev.sos.repository.ClientRepository;
import com.alves_dev.sos.util.ApiKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientAuthenticationService {

    private final ClientRepository clientRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    public ClientAuthenticationService(ClientRepository clientRepository, ApiKeyGenerator apiKeyGenerator) {
        this.clientRepository = clientRepository;
        this.apiKeyGenerator = apiKeyGenerator;
    }

    public AuthenticatedClient authenticate(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new InvalidApiKeyException("API key is required");
        }

        var client = clientRepository.findByApiKeyHash(apiKeyGenerator.hash(apiKey))
                .orElseThrow(() -> new InvalidApiKeyException("API key is invalid"));
        if (!client.isEnabled()) {
            throw new ClientDisabledException("Client is disabled");
        }
        return new AuthenticatedClient(client.getClientId(), client.isAdmin());
    }
}
