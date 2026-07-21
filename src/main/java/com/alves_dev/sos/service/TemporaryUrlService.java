package com.alves_dev.sos.service;

import com.alves_dev.sos.config.ServerConfig;
import com.alves_dev.sos.config.TemporaryUrlConfig;
import com.alves_dev.sos.exception.ExpiredTemporaryTokenException;
import com.alves_dev.sos.exception.InvalidExpirationException;
import com.alves_dev.sos.exception.InvalidTemporaryTokenException;
import com.alves_dev.sos.model.FileMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class TemporaryUrlService {

    private static final String PURPOSE = "TEMPORARY_DOWNLOAD";

    private final TemporaryUrlConfig config;
    private final ServerConfig serverConfig;
    private final ObjectMapper objectMapper;

    public TemporaryUrlService(TemporaryUrlConfig config, ServerConfig serverConfig, ObjectMapper objectMapper) {
        this.config = config;
        this.serverConfig = serverConfig;
        this.objectMapper = objectMapper;
    }

    public TemporaryUrl create(FileMetadata file, long expiresInSeconds) {
        if (expiresInSeconds < config.minDuration().toSeconds()
                || expiresInSeconds > config.maxDuration().toSeconds()) {
            throw new InvalidExpirationException("Expiration must be between "
                    + config.minDuration().toSeconds() + " and " + config.maxDuration().toSeconds() + " seconds");
        }
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);
        try {
            Payload payload = new Payload(file.getFileId(), expiresAt.getEpochSecond(),
                    UUID.randomUUID().toString(), PURPOSE);
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String token = encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
            String url = serverConfig.baseUrl() + "/files/" + file.getBucket() + "/" + file.getFileId()
                    + "?token=" + token;
            return new TemporaryUrl(url, expiresAt);
        } catch (Exception exception) {
            throw new InvalidTemporaryTokenException("Could not create temporary token");
        }
    }

    public void validate(String token, String expectedFileId) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new InvalidTemporaryTokenException("Temporary token is invalid");
            byte[] supplied = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), supplied)) {
                throw new InvalidTemporaryTokenException("Temporary token signature is invalid");
            }
            Payload payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Payload.class);
            if (!PURPOSE.equals(payload.purpose()) || !expectedFileId.equals(payload.fileId())) {
                throw new InvalidTemporaryTokenException("Temporary token is invalid for this file");
            }
            if (!Instant.now().isBefore(Instant.ofEpochSecond(payload.expiresAt()))) {
                throw new ExpiredTemporaryTokenException();
            }
        } catch (ExpiredTemporaryTokenException | InvalidTemporaryTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidTemporaryTokenException("Temporary token is malformed");
        }
    }

    private byte[] sign(String encodedPayload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
    }

    private record Payload(String fileId, long expiresAt, String tokenId, String purpose) {
    }

    public record TemporaryUrl(String url, Instant expiresAt) {
    }
}
