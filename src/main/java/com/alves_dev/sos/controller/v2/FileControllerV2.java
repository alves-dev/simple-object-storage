package com.alves_dev.sos.controller.v2;

import com.alves_dev.sos.exception.AccessDeniedException;
import com.alves_dev.sos.exception.InvalidMetadataException;
import com.alves_dev.sos.model.dto.ApiResponseDto;
import com.alves_dev.sos.model.dto.v2.FileResponse;
import com.alves_dev.sos.model.dto.v2.TemporaryUrlRequest;
import com.alves_dev.sos.model.dto.v2.TemporaryUrlResponse;
import com.alves_dev.sos.security.ClientContext;
import com.alves_dev.sos.service.BucketAuthorizationService;
import com.alves_dev.sos.service.BucketService;
import com.alves_dev.sos.service.FileContentCacheService;
import com.alves_dev.sos.service.FileMetadataService;
import com.alves_dev.sos.service.FileStorageService;
import com.alves_dev.sos.service.FileV2Service;
import com.alves_dev.sos.service.TemporaryUrlService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/files")
@Tag(name = "Protected files", description = "File management endpoints protected by X-API-Key.")
@SecurityRequirement(name = "apiKey")
public class FileControllerV2 {

    private static final Logger log = LoggerFactory.getLogger(FileControllerV2.class);

    private final FileV2Service fileV2Service;
    private final FileMetadataService metadataService;
    private final FileStorageService storageService;
    private final FileContentCacheService cacheService;
    private final BucketService bucketService;
    private final BucketAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final TemporaryUrlService temporaryUrlService;

    public FileControllerV2(FileV2Service fileV2Service, FileMetadataService metadataService,
                            FileStorageService storageService, FileContentCacheService cacheService,
                            BucketService bucketService, BucketAuthorizationService authorizationService,
                            ObjectMapper objectMapper, TemporaryUrlService temporaryUrlService) {
        this.fileV2Service = fileV2Service;
        this.metadataService = metadataService;
        this.storageService = storageService;
        this.cacheService = cacheService;
        this.bucketService = bucketService;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
        this.temporaryUrlService = temporaryUrlService;
    }

    @Operation(summary = "Create a temporary URL for a file")
    @PostMapping("/{fileId}/temporary-url")
    public ApiResponseDto<TemporaryUrlResponse> temporaryUrl(
            @PathVariable String fileId, @RequestBody TemporaryUrlRequest request) {
        var file = metadataService.findByFileIdOrThrow(fileId);
        var client = ClientContext.requireCurrentClient();
        if (Boolean.FALSE.equals(file.getIsPublic())) {
            authorizationService.requireAnyPermissionOrAdmin(client, bucketService.findOrThrow(file.getBucket()));
        }
        var temporary = temporaryUrlService.create(file, request.expiresInSeconds());
        return ApiResponseDto.success(new TemporaryUrlResponse(temporary.url(), temporary.expiresAt()));
    }

    @Operation(summary = "Upload a file")
    @PostMapping
    public ResponseEntity<ApiResponseDto<FileResponse>> upload(
            @RequestParam MultipartFile file,
            @RequestParam String bucket,
            @RequestParam(required = false) String filename,
            @RequestParam(defaultValue = "true") boolean isPublic,
            @RequestParam(required = false) String metadata,
            @RequestParam(defaultValue = "false") boolean forceReplace,
            @RequestParam(defaultValue = "false") boolean friendlyUrl) {
        Map<String, Object> parsedMetadata = parseMetadata(metadata);
        var result = fileV2Service.upload(file, bucket, filename, isPublic, parsedMetadata,
                StringUtils.hasText(metadata), forceReplace, friendlyUrl, ClientContext.requireCurrentClient());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponseDto.success(result.response()));
    }

    @Operation(summary = "Get file information")
    @GetMapping("/{fileId}/info")
    public ApiResponseDto<FileResponse> info(@PathVariable String fileId) {
        var file = metadataService.findByFileIdOrThrow(fileId);
        var client = ClientContext.requireCurrentClient();
        if (Boolean.FALSE.equals(file.getIsPublic())) {
            authorizationService.requireAnyPermissionOrAdmin(client, bucketService.findOrThrow(file.getBucket()));
        }
        return ApiResponseDto.success(fileV2Service.toResponse(file, client.admin() ? file.getAccessKey() : null));
    }

    @Operation(summary = "Delete a file")
    @DeleteMapping("/{fileId}")
    public ApiResponseDto<Void> delete(@PathVariable String fileId) {
        var file = metadataService.findByFileIdOrThrow(fileId);
        var client = ClientContext.requireCurrentClient();
        authorizationService.requireDelete(client, bucketService.findOrThrow(file.getBucket()));
        cacheService.invalidate(file);
        storageService.delete(file.getFilePath());
        metadataService.deleteByFileId(fileId);
        log.info("File deletion filename={} bucket={} clientId={}",
                file.getFilename(), file.getBucket(), client.clientId());
        return ApiResponseDto.successMessage("File deleted successfully");
    }

    private Map<String, Object> parseMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new InvalidMetadataException();
        }
    }
}
