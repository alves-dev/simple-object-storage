package com.alves_dev.sos.service;

import com.alves_dev.sos.config.ServerConfig;
import com.alves_dev.sos.config.TemporaryUrlConfig;
import com.alves_dev.sos.exception.InvalidExpirationException;
import com.alves_dev.sos.exception.InvalidTemporaryTokenException;
import com.alves_dev.sos.model.FileMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporaryUrlServiceTests {

    private final TemporaryUrlService service = new TemporaryUrlService(
            new TemporaryUrlConfig("a-test-secret-that-is-long-enough", Duration.ofMinutes(5), Duration.ofDays(30)),
            new ServerConfig("https://storage.example"),
            new ObjectMapper().findAndRegisterModules());

    @Test
    void createsUniqueValidTokensForTheSameFile() {
        FileMetadata file = file();

        var first = service.create(file, 600);
        var second = service.create(file, 600);
        String firstToken = first.url().substring(first.url().indexOf("?token=") + 7);
        String secondToken = second.url().substring(second.url().indexOf("?token=") + 7);

        assertThat(first.url()).startsWith("https://storage.example/files/documents/file-id?token=");
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThatCode(() -> service.validate(firstToken, "file-id")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validate(firstToken + "x", "file-id"))
                .isInstanceOf(InvalidTemporaryTokenException.class);
    }

    @Test
    void enforcesExpirationBounds() {
        assertThatThrownBy(() -> service.create(file(), 299))
                .isInstanceOf(InvalidExpirationException.class);
    }

    private FileMetadata file() {
        return FileMetadata.createV2("file-id", "documents", "report.pdf", "stored.pdf",
                "/tmp/stored.pdf", "application/pdf", 10L, true, null, null,
                "report.pdf", "report.pdf", true, "hash", "client");
    }
}
