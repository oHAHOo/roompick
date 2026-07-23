package com.roompick.global.common;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * RoomPick API에서 발생하는 공통 예외를 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반으로 발생한 예외를 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(
        BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn(
            "Business exception. code={}, message={}",
            errorCode.getCode(),
            exception.getMessage()
        );

        ErrorResponseDto body =
            ErrorResponseDto.from(errorCode);

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(body);
    }

    /**
     * Request DTO의 @Valid 검증 실패를 처리합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        List<ErrorResponseDto.ValidationErrorDto> errors =
            exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError ->
                    new ErrorResponseDto.ValidationErrorDto(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                    )
                )
                .toList();

        ErrorResponseDto body =
            ErrorResponseDto.of(errorCode, errors);

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(body);
    }

    /**
     * JSON 형식이나 날짜·시간 형식이 올바르지 않은 요청을 처리합니다.
     *
     * 예: HH:mm:ss 형식이 필요한 필드에 HH:mm을 전달한 경우
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        log.warn(
            "Invalid request body. message={}",
            exception.getMessage()
        );

        ErrorResponseDto body =
            ErrorResponseDto.from(errorCode);

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(body);
    }

    /**
     * 별도로 처리되지 않은 서버 내부 예외를 처리합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(
        Exception exception
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        log.error(
            "Unhandled exception",
            exception
        );

        ErrorResponseDto body =
            ErrorResponseDto.from(errorCode);

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(body);
    }
}
