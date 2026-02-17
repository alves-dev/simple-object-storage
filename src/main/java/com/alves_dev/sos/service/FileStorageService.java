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

            Path destination = bucketDir.resolve(storedFileName);
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
            Path path = Paths.get(filePath);
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
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file at path: " + filePath, e);
        }
    }

    private Path getBucketPath(String bucket) {
        return Paths.get(storageConfig.getRootPath(), bucket);
    }
}