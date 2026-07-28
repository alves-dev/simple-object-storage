package com.alves_dev.sos.controller.v2;

import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.dto.ApiResponseDto;
import com.alves_dev.sos.model.dto.v2.BucketPageResponse;
import com.alves_dev.sos.model.dto.v2.BucketResponse;
import com.alves_dev.sos.model.dto.v2.PageInfo;
import com.alves_dev.sos.model.dto.v2.FilePageResponse;
import com.alves_dev.sos.service.FileMetadataService;
import com.alves_dev.sos.service.FileV2Service;
import com.alves_dev.sos.service.RandomImageService;
import com.alves_dev.sos.security.ClientContext;
import com.alves_dev.sos.service.BucketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/buckets")
public class BucketControllerV2 {

    private final BucketService bucketService;
    private final FileMetadataService fileMetadataService;
    private final FileV2Service fileV2Service;
    private final RandomImageService randomImageService;

    public BucketControllerV2(BucketService bucketService, FileMetadataService fileMetadataService,
                              FileV2Service fileV2Service, RandomImageService randomImageService) {
        this.bucketService = bucketService;
        this.fileMetadataService = fileMetadataService;
        this.fileV2Service = fileV2Service;
        this.randomImageService = randomImageService;
    }

    @Operation(
            summary = "Get a random public image from a bucket",
            description = "Public endpoint. Only public available image files are eligible.",
            tags = {"Public content"}
    )
    @GetMapping("/{bucketName}/random-image")
    public ResponseEntity<byte[]> randomImage(@PathVariable String bucketName) {
        Bucket bucket = bucketService.findOrThrow(bucketName);
        var image = randomImageService.load(bucket.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.metadata().getMimeType()))
                .contentLength(image.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.bytes());
    }

    @Operation(summary = "List files in a bucket", tags = {"Protected buckets"},
            security = @SecurityRequirement(name = "apiKey"))
    @GetMapping("/{bucketName}/files")
    public ApiResponseDto<FilePageResponse> files(@PathVariable String bucketName,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Bucket bucket = bucketService.findOrThrow(bucketName);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        var result = fileMetadataService.findByBucket(
                bucket.getName(), PageRequest.of(Math.max(page, 0), boundedSize));
        var files = result.getContent().stream().map(file -> fileV2Service.toResponse(file, null)).toList();
        return ApiResponseDto.success(new FilePageResponse(files,
                new PageInfo(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages())));
    }

    @Operation(summary = "List buckets", tags = {"Protected buckets"},
            security = @SecurityRequirement(name = "apiKey"))
    @GetMapping
    public ApiResponseDto<BucketPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        var result = bucketService.list(PageRequest.of(Math.max(page, 0), boundedSize));
        var buckets = result.getContent().stream().map(bucket -> toResponse(bucket, null)).toList();
        return ApiResponseDto.success(new BucketPageResponse(
                buckets,
                new PageInfo(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages())));
    }

    @Operation(summary = "Get bucket details", tags = {"Protected buckets"},
            security = @SecurityRequirement(name = "apiKey"))
    @GetMapping("/{bucketName}")
    public ApiResponseDto<BucketResponse> details(@PathVariable String bucketName) {
        Bucket bucket = bucketService.findOrThrow(bucketName);
        return ApiResponseDto.success(toResponse(bucket, bucketService.countFiles(bucket)));
    }

    @Operation(summary = "Delete a bucket", tags = {"Protected buckets"},
            security = @SecurityRequirement(name = "apiKey"))
    @DeleteMapping("/{bucketName}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable String bucketName) {
        bucketService.delete(bucketName, ClientContext.requireCurrentClient());
        return ResponseEntity.ok(ApiResponseDto.successMessage("Bucket deleted successfully"));
    }

    private BucketResponse toResponse(Bucket bucket, Long fileCount) {
        return new BucketResponse(bucket.getName(), bucket.getCreatedByClientId(), bucket.isEnabled(),
                bucket.getCreatedAt(), bucket.getUpdatedAt(), fileCount);
    }
}
