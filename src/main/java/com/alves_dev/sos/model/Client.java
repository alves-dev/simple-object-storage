package com.alves_dev.sos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Locale;

@Document(collection = "clients")
public class Client {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clientId;

    private String name;

    @Indexed(unique = true)
    private String apiKeyHash;

    private boolean enabled;
    private boolean admin;
    private Instant createdAt;
    private Instant updatedAt;

    public Client(String clientId, String name, String apiKeyHash, boolean admin) {
        this.clientId = normalizeClientId(clientId);
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.enabled = true;
        this.admin = admin;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Client() {
    }

    public static String normalizeClientId(String clientId) {
        return clientId == null ? null : clientId.toLowerCase(Locale.ROOT);
    }

    public String getClientId() {
        return clientId;
    }

    public String getName() {
        return name;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAdmin() {
        return admin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }
}
