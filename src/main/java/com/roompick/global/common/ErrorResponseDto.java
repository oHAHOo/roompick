package com.roompick.global.common;

import java.util.List;

public record ErrorResponseDto(
        boolean success,
        String code,
        String message,
        List<ValidationErrorDto> errors
) {

    public static ErrorResponseDto from(ErrorCode errorCode) {
        return new ErrorResponseDto(false, errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponseDto of(ErrorCode errorCode, List<ValidationErrorDto> errors) {
        return new ErrorResponseDto(false, errorCode.getCode(), errorCode.getMessage(), errors);
    }

    public record ValidationErrorDto(
            String field,
            String message
    ) {
    }
}
