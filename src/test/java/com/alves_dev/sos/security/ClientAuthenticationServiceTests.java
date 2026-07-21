package com.alves_dev.sos.security;

import com.alves_dev.sos.exception.ClientDisabledException;
import com.alves_dev.sos.exception.InvalidApiKeyException;
import com.alves_dev.sos.model.Client;
import com.alves_dev.sos.repository.ClientRepository;
import com.alves_dev.sos.util.ApiKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAuthenticationServiceTests {

    private ClientRepository repository;
    private ApiKeyGenerator keyGenerator;
    private ClientAuthenticationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ClientRepository.class);
        keyGenerator = new ApiKeyGenerator();
        service = new ClientAuthenticationService(repository, keyGenerator);
    }

    @Test
    void authenticatesUsingOnlyTheHash() {
        String apiKey = "sos_secret";
        Client client = new Client("TEAM-A", "Team A", keyGenerator.hash(apiKey), true);
        when(repository.findByApiKeyHash(keyGenerator.hash(apiKey))).thenReturn(Optional.of(client));

        AuthenticatedClient authenticated = service.authenticate(apiKey);

        assertThat(authenticated).isEqualTo(new AuthenticatedClient("team-a", true));
        verify(repository).findByApiKeyHash(keyGenerator.hash(apiKey));
    }

    @Test
    void rejectsUnknownAndDisabledClients() {
        when(repository.findByApiKeyHash(keyGenerator.hash("invalid"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("invalid")).isInstanceOf(InvalidApiKeyException.class);

        Client disabled = new Client("disabled", "Disabled", keyGenerator.hash("key"), false);
        disabled.setEnabled(false);
        when(repository.findByApiKeyHash(keyGenerator.hash("key"))).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> service.authenticate("key")).isInstanceOf(ClientDisabledException.class);
    }
}
