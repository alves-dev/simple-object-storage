package com.alves_dev.sos.controller;

import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.dto.ApiResponseDto;
import com.alves_dev.sos.service.FileMetadataService;
import com.alves_dev.sos.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/files")
@Tag(name = "File content", description = "File content delivery operations")
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileMetadataService fileMetadataService;

    public FileController(FileStorageService fileStorageService,
                          FileMetadataService fileMetadataService) {
        this.fileStorageService = fileStorageService;
        this.fileMetadataService = fileMetadataService;
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Download a file", description = "Streams a file inline. Private files require the key query parameter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File content returned"),
            @ApiResponse(responseCode = "403", description = "Access key required or invalid"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<?> serveFile(
            @Parameter(description = "Public file identifier", required = true, example = "a1b2c3d4")
            @PathVariable String fileId,
            @Parameter(description = "Access key for private files", in = ParameterIn.QUERY)
            @RequestParam(value = "key", required = false) String key) {

        FileMetadata metadata = fileMetadataService.findByFileIdOrThrow(fileId);

        // Access control for private files
        if (Boolean.FALSE.equals(metadata.getIsPublic())) {
            if (!StringUtils.hasText(key)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponseDto.error("ACCESS_DENIED", "Access key is required for private files"));
            }
            if (!key.equals(metadata.getAccessKey())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiResponseDto.error("ACCESS_DENIED", "Invalid access key"));
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
