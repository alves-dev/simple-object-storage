package com.alves_dev.sos.controller;

import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.dto.ApiResponse;
import com.alves_dev.sos.service.FileMetadataService;
import com.alves_dev.sos.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileMetadataService fileMetadataService;

    public FileController(FileStorageService fileStorageService,
                          FileMetadataService fileMetadataService) {
        this.fileStorageService = fileStorageService;
        this.fileMetadataService = fileMetadataService;
    }

    @GetMapping("/{fileId}")
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
}