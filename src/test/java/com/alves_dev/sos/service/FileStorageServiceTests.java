package com.alves_dev.sos.service;

import com.alves_dev.sos.config.StorageConfig;
import com.alves_dev.sos.exception.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTests {

    @TempDir
    Path root;

    @Test
    void storesMovesAndReadsOnlyInsideConfiguredRoot() {
        FileStorageService service = service();
        var multipart = new MockMultipartFile("file", "hello.txt", "text/plain", "hello".getBytes());

        String temporary = service.storeTemporary(multipart, "docs");
        String stored = service.moveTemporaryToFinal(temporary, "docs", "internal.txt");

        assertThat(service.exists(stored)).isTrue();
        assertThat(service.readAllBytes(stored)).isEqualTo("hello".getBytes());
        assertThatThrownBy(() -> service.resolveSafePath(root.resolve("../outside").toString()))
                .isInstanceOf(StorageException.class);
    }

    private FileStorageService service() {
        return new FileStorageService(new StorageConfig(
                root.toString(), 1024L, "^[a-zA-Z0-9_-]+$", "^[a-zA-Z0-9._-]+$", 50));
    }
}
