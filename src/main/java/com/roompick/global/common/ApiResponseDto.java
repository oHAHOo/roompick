package com.roompick.global.common;

public record ApiResponseDto<T>(
        boolean success,
        String message,
        T data
) {

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(true, message, data);
    }

    public static ApiResponseDto<Void> success(String message) {
        return new ApiResponseDto<>(true, message, null);
    }
}
