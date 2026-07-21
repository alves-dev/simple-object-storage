package com.alves_dev.sos.controller;

import com.alves_dev.sos.model.dto.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Deprecated
@RestController
@RequestMapping("/api/files")
public class FileApiController {

    @Deprecated
    @Operation(deprecated = true, summary = "Use POST /api/v2/files")
    @PostMapping("/upload")
    public ResponseEntity<ApiResponseDto<Void>> uploadGone() {
        return gone("POST /api/v2/files");
    }

    @Deprecated
    @Operation(deprecated = true, summary = "Use GET /api/v2/files/{fileId}/info")
    @GetMapping("/{fileId}/info")
    public ResponseEntity<ApiResponseDto<Void>> infoGone(@PathVariable String fileId) {
        return gone("GET /api/v2/files/" + fileId + "/info");
    }

    @Deprecated
    @Operation(deprecated = true, summary = "Use DELETE /api/v2/files/{fileId}")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponseDto<Void>> deleteGone(@PathVariable String fileId) {
        return gone("DELETE /api/v2/files/" + fileId);
    }

    @Deprecated
    @Operation(deprecated = true, summary = "Use GET /api/v2/buckets/{bucketName}/files")
    @GetMapping("/bucket/{bucketName}")
    public ResponseEntity<ApiResponseDto<Void>> bucketGone(@PathVariable String bucketName) {
        return gone("GET /api/v2/buckets/" + bucketName + "/files");
    }

    private ResponseEntity<ApiResponseDto<Void>> gone(String replacement) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponseDto.error("ENDPOINT_GONE", "This endpoint was replaced by " + replacement));
    }
}
