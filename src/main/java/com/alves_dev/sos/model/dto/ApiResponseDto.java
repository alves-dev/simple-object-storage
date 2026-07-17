package com.alves_dev.sos.model.dto;

public record ApiResponseDto<T>(
        boolean success,
        T data,
        String message,
        ApiError error
) {

    public static <T> ApiResponseDto<T> success(T data) {
        return new ApiResponseDto<>(true, data, null, null);
    }

    public static <T> ApiResponseDto<T> successMessage(String message) {
        return new ApiResponseDto<>(true, null, message, null);
    }

    public static <T> ApiResponseDto<T> error(String code, String message) {
        return new ApiResponseDto<>(false, null, null, new ApiError(code, message));
    }

    public record ApiError(
            String code,
            String message
    ) {}
}
