package com.alves_dev.sos.service;

import com.alves_dev.sos.config.StorageConfig;
import com.alves_dev.sos.exception.StorageException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.UUID;

@Service
public class FileStorageService {

    private final StorageConfig storageConfig;

    public FileStorageService(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    /**
     * Saves a file to the filesystem under the specified bucket directory.
     *
     * @return the absolute path where the file was saved
     */
    public String store(MultipartFile file, String bucket, String storedFileName) {
        try {
            Path bucketDir = getBucketPath(bucket);
            Files.createDirectories(bucketDir);

            Path destination = resolveSafePath(bucketDir.resolve(storedFileName).toString());
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            return destination.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a file as a Spring Resource for streaming to the HTTP response.
     */
    public Resource loadAsResource(String filePath) {
        try {
            Path path = resolveSafePath(filePath);
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new StorageException("Cannot read file at path: " + filePath, null);
            }

            return resource;
        } catch (MalformedURLException e) {
            throw new StorageException("Invalid file path: " + filePath, e);
        }
    }

    /**
     * Deletes a file from the filesystem.
     */
    public void delete(String filePath) {
        try {
            Path path = resolveSafePath(filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file at path: " + filePath, e);
        }
    }

    private Path getBucketPath(String bucket) {
        return resolveSafePath(root().resolve(bucket).toString());
    }

    public String storeTemporary(MultipartFile file, String bucket) {
        try {
            Path bucketDir = getBucketPath(bucket);
            Files.createDirectories(bucketDir);
            Path temporary = bucketDir.resolve(".sos-tmp-" + UUID.randomUUID());
            Files.copy(file.getInputStream(), temporary);
            return temporary.toString();
        } catch (IOException exception) {
            throw new StorageException("Failed to store temporary file", exception);
        }
    }

    public String moveTemporaryToFinal(String temporaryPath, String bucket, String storedFileName) {
        Path temporary = resolveSafePath(temporaryPath);
        Path target = resolveSafePath(getBucketPath(bucket).resolve(storedFileName).toString());
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toString();
        } catch (IOException exception) {
            throw new StorageException("Failed to finalize stored file", exception);
        }
    }

    public void deleteIfExists(String filePath) {
        if (filePath == null) {
            return;
        }
        delete(filePath);
    }

    public boolean exists(String filePath) {
        return filePath != null && Files.isRegularFile(resolveSafePath(filePath));
    }

    public long size(String filePath) {
        try {
            return Files.size(resolveSafePath(filePath));
        } catch (IOException exception) {
            throw new StorageException("Failed to read stored file size", exception);
        }
    }

    public byte[] readAllBytes(String filePath) {
        try {
            Path path = resolveSafePath(filePath);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new StorageException("Stored file is unavailable", null);
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new StorageException("Failed to read stored file", exception);
        }
    }

    public Path resolveSafePath(String filePath) {
        Path root = root();
        Path candidate = Paths.get(filePath);
        if (!candidate.isAbsolute()) {
            candidate = root.resolve(candidate);
        }
        candidate = candidate.toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw new StorageException("Resolved path is outside the storage root", null);
        }
        return candidate;
    }

    public Path root() {
        return Paths.get(storageConfig.rootPath()).toAbsolutePath().normalize();
    }
}
