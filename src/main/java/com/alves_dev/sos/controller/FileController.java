package com.alves_dev.sos.controller;

import com.alves_dev.sos.config.ServerConfig;
import com.alves_dev.sos.config.StorageConfig;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.dto.ApiResponse;
import com.alves_dev.sos.model.dto.BucketListResponse;
import com.alves_dev.sos.model.dto.FileInfoResponse;
import com.alves_dev.sos.model.dto.UploadResponse;
import com.alves_dev.sos.service.FileMetadataService;
import com.alves_dev.sos.service.FileStorageService;
import com.alves_dev.sos.util.AccessKeyGenerator;
import com.alves_dev.sos.util.FileNameGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileMetadataService fileMetadataService;
    private final FileNameGenerator fileNameGenerator;
    private final AccessKeyGenerator accessKeyGenerator;
    private final StorageConfig storageConfig;
    private final ServerConfig serverConfig;
    private final ObjectMapper objectMapper;

    public FileController(FileStorageService fileStorageService,
                          FileMetadataService fileMetadataService,
                          FileNameGenerator fileNameGenerator,
                          AccessKeyGenerator accessKeyGenerator,
                          StorageConfig storageConfig,
                          ServerConfig serverConfig,
                          ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.fileMetadataService = fileMetadataService;
        this.fileNameGenerator = fileNameGenerator;
        this.accessKeyGenerator = accessKeyGenerator;
        this.storageConfig = storageConfig;
        this.serverConfig = serverConfig;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/files/upload")
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam(value = "filename", required = false) String customFilename,
            @RequestParam(value = "isPublic", defaultValue = "true") Boolean isPublic,
            @RequestParam(value = "metadata", required = false) String metadataJson) {

        // Validate bucket name
        if (!StringUtils.hasText(bucket)
                || bucket.length() > storageConfig.maxBucketNameLength()
                || !bucket.matches(storageConfig.allowedBucketsRegex())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_BUCKET",
                            "Bucket name must contain only alphanumeric characters, hyphens and underscores"));
        }

        // Determine effective filename
        String originalFilename = StringUtils.hasText(customFilename)
                ? customFilename
                : file.getOriginalFilename();

        if (!StringUtils.hasText(originalFilename)) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_FILENAME", "Could not determine a valid filename"));
        }

        // Validate custom filename if provided
        if (StringUtils.hasText(customFilename)
                && !customFilename.matches(storageConfig.allowedFilenameRegex())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_FILENAME",
                            "Filename must contain only alphanumeric characters, dots, hyphens and underscores"));
        }

        // Parse optional metadata JSON
        Map<String, Object> metadata = null;
        if (StringUtils.hasText(metadataJson)) {
            try {
                metadata = objectMapper.readValue(metadataJson, new TypeReference<>() {
                });
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("INVALID_METADATA", "Metadata must be a valid JSON object"));
            }
        }

        // Generate IDs and names
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String storedFileName = fileNameGenerator.generate(originalFilename);

        // Persist to filesystem
        String filePath = fileStorageService.store(file, bucket, storedFileName);

        // Generate access key for private files
        String accessKey = Boolean.FALSE.equals(isPublic) ? accessKeyGenerator.generate() : null;

        // Detect MIME type
        String mimeType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // Build and save metadata
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setFileId(fileId);
        fileMetadata.setBucket(bucket);
        fileMetadata.setOriginalFileName(file.getOriginalFilename());
        fileMetadata.setStoredFileName(storedFileName);
        fileMetadata.setFilePath(filePath);
        fileMetadata.setMimeType(mimeType);
        fileMetadata.setFileSize(file.getSize());
        fileMetadata.setIsPublic(isPublic);
        fileMetadata.setAccessKey(accessKey);
        fileMetadata.setUploadedAt(Instant.now());
        fileMetadata.setMetadata(metadata);

        fileMetadataService.save(fileMetadata);

        // Build response URLs
        String baseUrl = serverConfig.baseUrl();
        String fileUrl = baseUrl + "/files/" + fileId;
        String privateUrl = (accessKey != null) ? fileUrl + "?key=" + accessKey : null;

        UploadResponse response = new UploadResponse(
                fileId,
                bucket,
                originalFilename,
                fileUrl,
                isPublic,
                accessKey,
                privateUrl,
                file.getSize(),
                mimeType,
                fileMetadata.getUploadedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<?> serveFile(
            @PathVariable String fileId,
            @RequestParam(value = "key", required = false) String key) {

        FileMetadata metadata = fileMetadataService.findByFileIdOrThrow(fileId);

        // Access control for private files
        if (Boolean.FALSE.equals(metadata.getIsPublic())) {
            if (!StringUtils.hasText(key)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponse.error("ACCESS_DENIED", "Access key is required for private files"));
            }
            if (!key.equals(metadata.getAccessKey())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponse.error("ACCESS_DENIED", "Invalid access key"));
            }
        }

        Resource resource = fileStorageService.loadAsResource(metadata.getFilePath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + metadata.getOriginalFileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(metadata.getFileSize()))
                .body(resource);
    }

    @GetMapping("/api/files/{fileId}/info")
    public ResponseEntity<ApiResponse<FileInfoResponse>> getFileInfo(
            @PathVariable String fileId,
            @RequestParam(value = "key", required = false) String key) {

        FileMetadata metadata = fileMetadataService.findByFileIdOrThrow(fileId);

        // Access control for private files
        if (Boolean.FALSE.equals(metadata.getIsPublic())) {
            if (!StringUtils.hasText(key) || !key.equals(metadata.getAccessKey())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponse.error("ACCESS_DENIED", "Access key is required for private files"));
            }
        }

        FileInfoResponse info = new FileInfoResponse(
                metadata.getFileId(),
                metadata.getBucket(),
                metadata.getOriginalFileName(),
                metadata.getMimeType(),
                metadata.getFileSize(),
                metadata.getIsPublic(),
                metadata.getUploadedAt(),
                metadata.getMetadata());

        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @DeleteMapping("/api/files/{fileId}")
    public ResponseEntity<ApiResponse<String>> deleteFile(
            @PathVariable String fileId,
            @RequestHeader(value = "X-Access-Key", required = false) String accessKey) {

        FileMetadata metadata = fileMetadataService.findByFileIdOrThrow(fileId);

        // For private files, require the X-Access-Key header
        if (Boolean.FALSE.equals(metadata.getIsPublic())) {
            if (!StringUtils.hasText(accessKey) || !accessKey.equals(metadata.getAccessKey())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponse.error("ACCESS_DENIED", "Access key is required to delete a private file"));
            }
        }

        // Delete from filesystem, then from MongoDB
        fileStorageService.delete(metadata.getFilePath());
        fileMetadataService.deleteByFileId(fileId);

        return ResponseEntity.ok(ApiResponse.successMessage("File deleted successfully"));
    }

    @GetMapping("/api/files/bucket/{bucketName}")
    public ResponseEntity<ApiResponse<BucketListResponse>> listBucket(
            @PathVariable String bucketName,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Page<FileMetadata> resultPage = fileMetadataService.findByBucket(
                bucketName, PageRequest.of(page, size));

        List<BucketListResponse.FileEntry> files = resultPage.getContent().stream()
                .map(f -> new BucketListResponse.FileEntry(
                        f.getFileId(),
                        f.getOriginalFileName(),
                        f.getFileSize(),
                        f.getIsPublic(),
                        f.getUploadedAt()))
                .collect(Collectors.toList());

        BucketListResponse.Pagination pagination = new BucketListResponse.Pagination(
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages());

        BucketListResponse body = new BucketListResponse(bucketName, files, pagination);

        return ResponseEntity.ok(ApiResponse.success(body));
    }
}