package com.alves_dev.sos.controller;

import com.alves_dev.sos.config.ServerConfig;
import com.alves_dev.sos.config.StorageConfig;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.dto.ApiResponseDto;
import com.alves_dev.sos.model.dto.BucketListResponse;
import com.alves_dev.sos.model.dto.FileInfoResponse;
import com.alves_dev.sos.model.dto.UploadResponse;
import com.alves_dev.sos.service.FileMetadataService;
import com.alves_dev.sos.service.FileStorageService;
import com.alves_dev.sos.util.AccessKeyGenerator;
import com.alves_dev.sos.util.FileNameGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File upload, metadata, listing and deletion operations")
public class FileApiController {

    private final FileStorageService fileStorageService;
    private final FileMetadataService fileMetadataService;
    private final FileNameGenerator fileNameGenerator;
    private final AccessKeyGenerator accessKeyGenerator;
    private final StorageConfig storageConfig;
    private final ServerConfig serverConfig;
    private final ObjectMapper objectMapper;

    public FileApiController(FileStorageService fileStorageService,
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

    @PostMapping("/upload")
    @Operation(summary = "Upload a file", description = "Uploads a public or private file to a bucket.",
            security = @SecurityRequirement(name = "apiKey"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid upload data"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    })
    public ResponseEntity<ApiResponseDto<UploadResponse>> upload(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Bucket name: letters, numbers, hyphens and underscores; maximum 50 characters",
                    required = true, example = "documents")
            @RequestParam("bucket") String bucket,
            @Parameter(description = "Optional filename override", example = "report.pdf")
            @RequestParam(value = "filename", required = false) String customFilename,
            @Parameter(description = "Whether the file can be accessed without an access key")
            @RequestParam(value = "isPublic", defaultValue = "true") Boolean isPublic,
            @Parameter(description = "Optional JSON object with custom metadata", example = "{\"owner\":\"team-a\"}")
            @RequestParam(value = "metadata", required = false) String metadataJson) {

        // Validate bucket name
        if (!StringUtils.hasText(bucket)
                || bucket.length() > storageConfig.maxBucketNameLength()
                || !bucket.matches(storageConfig.allowedBucketsRegex())) {
            return ResponseEntity.badRequest().body(
                    ApiResponseDto.error("INVALID_BUCKET",
                            "Bucket name must contain only alphanumeric characters, hyphens and underscores"));
        }

        // Determine effective filename
        String originalFilename = StringUtils.hasText(customFilename)
                ? customFilename
                : file.getOriginalFilename();

        if (!StringUtils.hasText(originalFilename)) {
            return ResponseEntity.badRequest().body(
                    ApiResponseDto.error("INVALID_FILENAME", "Could not determine a valid filename"));
        }

        // Validate custom filename if provided
        if (StringUtils.hasText(customFilename)
                && !customFilename.matches(storageConfig.allowedFilenameRegex())) {
            return ResponseEntity.badRequest().body(
                    ApiResponseDto.error("INVALID_FILENAME",
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
                        ApiResponseDto.error("INVALID_METADATA", "Metadata must be a valid JSON object"));
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
        FileMetadata fileMetadata = new FileMetadata(
                fileId,
                bucket,
                file.getOriginalFilename(),
                storedFileName,
                filePath,
                mimeType,
                file.getSize(),
                isPublic,
                accessKey,
                metadata
        );

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

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(response));
    }

    @GetMapping("/{fileId}/info")
    @Operation(summary = "Get file information", description = "Returns metadata for a file. Private files require the key query parameter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File information returned"),
            @ApiResponse(responseCode = "403", description = "Access key required or invalid"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<ApiResponseDto<FileInfoResponse>> getFileInfo(
            @Parameter(description = "Public file identifier", required = true, example = "a1b2c3d4")
            @PathVariable String fileId,
            @Parameter(description = "Access key for private files", in = ParameterIn.QUERY)
            @RequestParam(value = "key", required = false) String key) {

        FileMetadata metadata = fileMetadataService.findByFileIdOrThrow(fileId);

        // Access control for private files
        if (Boolean.FALSE.equals(metadata.getIsPublic())) {
            if (!StringUtils.hasText(key) || !key.equals(metadata.getAccessKey())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponseDto.error("ACCESS_DENIED", "Access key is required for private files"));
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

        return ResponseEntity.ok(ApiResponseDto.success(info));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Delete a file", description = "Deletes the file from storage and its metadata.",
            security = @SecurityRequirement(name = "apiKey"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "403", description = "Access key required or invalid"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<ApiResponseDto<String>> deleteFile(
            @Parameter(description = "Public file identifier", required = true, example = "a1b2c3d4")
            @PathVariable String fileId,
            @Parameter(description = "Access key for private files", in = ParameterIn.HEADER)
            @RequestHeader(value = "X-Access-Key", required = false) String accessKey) {

        FileMetadata metadata = fileMetadataService.findByFileIdOrThrow(fileId);

        // For private files, require the X-Access-Key header
        if (Boolean.FALSE.equals(metadata.getIsPublic())) {
            if (!StringUtils.hasText(accessKey) || !accessKey.equals(metadata.getAccessKey())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponseDto.error("ACCESS_DENIED", "Access key is required to delete a private file"));
            }
        }

        // Delete from filesystem, then from MongoDB
        fileStorageService.delete(metadata.getFilePath());
        fileMetadataService.deleteByFileId(fileId);

        return ResponseEntity.ok(ApiResponseDto.successMessage("File deleted successfully"));
    }

    @GetMapping("/bucket/{bucketName}")
    @Operation(summary = "List files in a bucket", description = "Returns a paginated list of files belonging to the bucket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bucket files returned"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters")
    })
    public ResponseEntity<ApiResponseDto<BucketListResponse>> listBucket(
            @Parameter(description = "Bucket name", required = true, example = "documents")
            @PathVariable String bucketName,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Number of files per page", example = "20")
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

        return ResponseEntity.ok(ApiResponseDto.success(body));
    }
}
