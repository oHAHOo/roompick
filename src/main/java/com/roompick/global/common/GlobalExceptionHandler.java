package com.roompick.global.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(
        BusinessException exception
    ) {
        ErrorCode errorCode =
            exception.getErrorCode();

        log.warn(
            "Business exception. code={}, message={}",
            errorCode.getCode(),
            exception.getMessage()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponseDto.from(errorCode)
            );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto>
    handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        ErrorCode errorCode =
            ErrorCode.INVALID_INPUT_VALUE;

        log.warn(
            "Validation failed. message={}",
            exception.getMessage()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponseDto.from(errorCode)
            );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto>
    handleHttpMessageNotReadableException(
        HttpMessageNotReadableException exception
    ) {
        ErrorCode errorCode =
            ErrorCode.INVALID_INPUT_VALUE;

        log.warn(
            "Invalid request body. message={}",
            exception.getMessage()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponseDto.from(errorCode)
            );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(
        Exception exception
    ) {
        ErrorCode errorCode =
            ErrorCode.INTERNAL_SERVER_ERROR;

        log.error(
            "Unhandled exception",
            exception
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponseDto.from(errorCode)
            );
    }
}
