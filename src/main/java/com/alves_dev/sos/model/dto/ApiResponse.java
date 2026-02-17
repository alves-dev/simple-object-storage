package com.alves_dev.sos.model.dto;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        ApiError error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> successMessage(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message));
    }

    public record ApiError(
            String code,
            String message
    ) {}
}