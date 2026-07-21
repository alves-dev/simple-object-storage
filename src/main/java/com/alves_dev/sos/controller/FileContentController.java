package com.alves_dev.sos.controller;

import com.alves_dev.sos.exception.AccessDeniedException;
import com.alves_dev.sos.exception.InvalidTemporaryTokenException;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.service.FileAccessTrackingService;
import com.alves_dev.sos.service.FileContentCacheService;
import com.alves_dev.sos.service.FileContentService;
import com.alves_dev.sos.service.TemporaryUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/files")
public class FileContentController {

    private final FileContentService contentService;
    private final FileContentCacheService cacheService;
    private final TemporaryUrlService temporaryUrlService;
    private final FileAccessTrackingService trackingService;

    public FileContentController(FileContentService contentService, FileContentCacheService cacheService,
                                 TemporaryUrlService temporaryUrlService,
                                 FileAccessTrackingService trackingService) {
        this.contentService = contentService;
        this.cacheService = cacheService;
        this.temporaryUrlService = temporaryUrlService;
        this.trackingService = trackingService;
    }

    @RequestMapping(value = "/{fileId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> legacy(@PathVariable String fileId,
                                         @RequestParam(required = false) String key,
                                         @RequestParam(required = false) String token,
                                         HttpServletRequest request) {
        return serve(contentService.resolveLegacy(fileId), key, token, request);
    }

    @RequestMapping(value = "/{bucketName}/{value}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> v2(@PathVariable String bucketName, @PathVariable String value,
                                     @RequestParam(required = false) String key,
                                     @RequestParam(required = false) String token,
                                     HttpServletRequest request) {
        return serve(contentService.resolveV2(bucketName, value), key, token, request);
    }

    private ResponseEntity<byte[]> serve(FileMetadata file, String key, String token, HttpServletRequest request) {
        boolean temporary = authorize(file, key, token);
        String etag = cacheService.etag(file);
        HttpHeaders headers = headers(file, etag, temporary);
        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
        }
        if (HttpMethod.HEAD.matches(request.getMethod())) {
            return new ResponseEntity<>(null, headers, HttpStatus.OK);
        }
        byte[] bytes = contentService.load(file);
        trackingService.record(file.getFileId());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private boolean authorize(FileMetadata file, String key, String token) {
        RuntimeException tokenFailure = null;
        if (StringUtils.hasText(token)) {
            try {
                temporaryUrlService.validate(token, file.getFileId());
                return true;
            } catch (InvalidTemporaryTokenException |
                     com.alves_dev.sos.exception.ExpiredTemporaryTokenException exception) {
                tokenFailure = exception;
            }
        }
        if (Boolean.TRUE.equals(file.getIsPublic())) {
            return false;
        }
        if (StringUtils.hasText(key) && key.equals(file.getAccessKey())) {
            return false;
        }
        if (tokenFailure != null) throw tokenFailure;
        throw new AccessDeniedException("A valid access key or temporary token is required");
    }

    private HttpHeaders headers(FileMetadata file, String etag, boolean temporary) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getMimeType()));
        headers.setContentLength(file.getFileSize());
        headers.setETag(etag);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFilename(file) + "\"");
        if (temporary || Boolean.FALSE.equals(file.getIsPublic())) {
            headers.setCacheControl(CacheControl.noStore().cachePrivate());
        } else {
            headers.setCacheControl(CacheControl.maxAge(5, TimeUnit.HOURS).cachePublic());
        }
        return headers;
    }

    private String safeFilename(FileMetadata file) {
        String original = file.getOriginalFileName();
        if (!StringUtils.hasText(original)) return "file";
        String ascii = new String(original.getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII)
                .replaceAll("[\\r\\n\"\\\\]", "_");
        return ascii.isBlank() ? "file" : ascii;
    }
}
