package com.alves_dev.sos.exception;

import com.alves_dev.sos.model.dto.ApiResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleFileNotFound(FileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDto.error("FILE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.error("ACCESS_DENIED", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidApiKeyException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidApiKey(InvalidApiKeyException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error("INVALID_API_KEY", ex.getMessage()));
    }

    @ExceptionHandler(ClientDisabledException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleClientDisabled(ClientDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.error("CLIENT_DISABLED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidBucketException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidBucket(InvalidBucketException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error("INVALID_BUCKET", ex.getMessage()));
    }

    @ExceptionHandler(BucketNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBucketNotFound(BucketNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDto.error("BUCKET_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BucketDisabledException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBucketDisabled(BucketDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.error("BUCKET_DISABLED", ex.getMessage()));
    }

    @ExceptionHandler(BucketNotEmptyException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBucketNotEmpty(BucketNotEmptyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseDto.error("BUCKET_NOT_EMPTY", ex.getMessage()));
    }

    @ExceptionHandler(InvalidFilenameException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidFilename(InvalidFilenameException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error("INVALID_FILENAME", ex.getMessage()));
    }

    @ExceptionHandler(InvalidMetadataException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidMetadata(InvalidMetadataException ex) {
        return ResponseEntity.badRequest().body(ApiResponseDto.error("INVALID_METADATA", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateFilenameException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDuplicateFilename(DuplicateFilenameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseDto.error("DUPLICATE_FILENAME", ex.getMessage()));
    }

    @ExceptionHandler(InvalidExpirationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidExpiration(InvalidExpirationException ex) {
        return ResponseEntity.badRequest().body(ApiResponseDto.error("INVALID_EXPIRATION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTemporaryTokenException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidToken(InvalidTemporaryTokenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.error("INVALID_TEMPORARY_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(ExpiredTemporaryTokenException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleExpiredToken(ExpiredTemporaryTokenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.error("EXPIRED_TEMPORARY_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(ContentNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingContent(ContentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDto.error("FILE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleStorageError(StorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error("STORAGE_ERROR", "An error occurred while processing the file"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMaxSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error("FILE_TOO_LARGE", "File exceeds the maximum allowed size"));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleFileTooLarge(FileTooLargeException ex) {
        return ResponseEntity.badRequest().body(ApiResponseDto.error("FILE_TOO_LARGE", ex.getMessage()));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleVersionConflict(VersionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseDto.error("VERSION_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponseDto.error("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
