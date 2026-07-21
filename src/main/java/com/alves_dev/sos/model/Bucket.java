package com.alves_dev.sos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Locale;

@Document(collection = "buckets")
public class Bucket {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String createdByClientId;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public Bucket(String name, String createdByClientId) {
        this.name = normalizeName(name);
        this.createdByClientId = Client.normalizeClientId(createdByClientId);
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Bucket() {
    }

    public static String normalizeName(String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public String getCreatedByClientId() {
        return createdByClientId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
