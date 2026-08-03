package com.roompick.global.common;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.roompick.global.common.ErrorResponseDto.ValidationErrorDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 애플리케이션에서 발생하는 예외를 공통 응답 형식으로 변환합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반 오류를 처리합니다.
     *
     * 구체적인 필드 오류가 전달된 경우에만 errors에 포함하고,
     * 일반 비즈니스 오류는 빈 errors 배열을 유지합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(
        BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        List<ValidationErrorDto> errors =
            exception.getFieldErrors()
                .stream()
                .map(this::toBusinessValidationError)
                .toList();

        ErrorResponseDto body =
            ErrorResponseDto.of(
                errorCode,
                errors
            );

        ResponseEntity<ErrorResponseDto> response =
            ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);

        return response;
    }

    /**
     * DTO의 Bean Validation 오류를 처리합니다.
     *
     * 잘못된 요청 필드명과 검증 메시지를 errors 배열에 담아 반환합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(
        MethodArgumentNotValidException exception
    ) {
        List<ValidationErrorDto> errors =
            exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationError)
                .toList();

        ErrorCode errorCode =
            ErrorCode.INVALID_INPUT_VALUE;

        ErrorResponseDto body =
            ErrorResponseDto.of(
                errorCode,
                errors
            );

        ResponseEntity<ErrorResponseDto> response =
            ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);

        return response;
    }

    /**
     * 요청 Body의 JSON 문법이나 필드 타입이 올바르지 않은 경우를 처리합니다.
     *
     * DTO 생성 이전에 발생하는 오류이므로
     * Bean Validation과 별도의 예외 처리가 필요합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto>
    handleHttpMessageNotReadableException(
        HttpMessageNotReadableException exception
    ) {
        ErrorCode errorCode =
            ErrorCode.INVALID_INPUT_VALUE;

        ValidationErrorDto validationError =
            toMessageNotReadableError(exception);

        ErrorResponseDto body =
            ErrorResponseDto.of(
                errorCode,
                List.of(validationError)
            );

        ResponseEntity<ErrorResponseDto> response =
            ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);

        return response;
    }

    /**
     * Query Parameter나 Path Variable의 타입 변환 오류를 처리합니다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatchException(
        MethodArgumentTypeMismatchException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        ErrorResponseDto body = ErrorResponseDto.of(
            errorCode,
            List.of(
                new ValidationErrorDto(
                    exception.getName(),
                    "요청 값의 형식이 올바르지 않습니다."
                )
            )
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(body);
    }

    /**
     * 별도로 처리하지 못한 예외를 서버 내부 오류로 처리합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(
        Exception exception
    ) {
        log.error(
            "Unhandled exception",
            exception
        );

        ErrorCode errorCode =
            ErrorCode.INTERNAL_SERVER_ERROR;

        ErrorResponseDto body =
            ErrorResponseDto.from(errorCode);

        ResponseEntity<ErrorResponseDto> response =
            ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);

        return response;
    }

    /**
     * Bean Validation의 필드 오류를 공통 응답 형식으로 변환합니다.
     */
    private ValidationErrorDto toValidationError(
        FieldError fieldError
    ) {
        return new ValidationErrorDto(
            fieldError.getField(),
            fieldError.getDefaultMessage()
        );
    }

    /**
     * 비즈니스 필드 오류를 공통 응답 형식으로 변환합니다.
     */
    private ValidationErrorDto toBusinessValidationError(
        BusinessException.BusinessFieldError fieldError
    ) {
        return new ValidationErrorDto(
            fieldError.field(),
            fieldError.message()
        );
    }

    /**
     * 요청 Body 변환 오류에서 필드명과 사용자 안내 메시지를 추출합니다.
     */
    private ValidationErrorDto toMessageNotReadableError(
        HttpMessageNotReadableException exception
    ) {
        JsonMappingException mappingException =
            findJsonMappingException(exception);

        /*
         * JSON 문법 자체가 잘못돼 필드 위치를 특정할 수 없는 경우입니다.
         */
        if (mappingException == null) {
            return new ValidationErrorDto(
                "requestBody",
                "요청 Body의 JSON 형식이 올바르지 않습니다."
            );
        }

        String field =
            extractField(mappingException);

        /*
         * 문자열을 날짜나 숫자로 변환하지 못한 경우에는
         * Jackson이 InvalidFormatException을 발생시킵니다.
         */
        if (
            mappingException
                instanceof InvalidFormatException invalidFormatException
        ) {
            Class<?> targetType =
                invalidFormatException.getTargetType();

            if (LocalDate.class.equals(targetType)) {
                return new ValidationErrorDto(
                    field,
                    "날짜 형식은 yyyy-MM-dd여야 합니다."
                );
            }

            if (Number.class.isAssignableFrom(targetType)) {
                return new ValidationErrorDto(
                    field,
                    "숫자 형식으로 입력해야 합니다."
                );
            }
        }

        return new ValidationErrorDto(
            field,
            "요청 값의 형식이 올바르지 않습니다."
        );
    }

    /**
     * 예외 원인 체인에서 Jackson의 필드 변환 예외를 찾습니다.
     */
    private JsonMappingException findJsonMappingException(
        Throwable throwable
    ) {
        Throwable current =
            throwable;

        while (current != null) {
            if (
                current
                    instanceof JsonMappingException mappingException
            ) {
                return mappingException;
            }

            current =
                current.getCause();
        }

        return null;
    }

    /**
     * JSON 변환에 실패한 마지막 필드명을 반환합니다.
     */
    private String extractField(
        JsonMappingException exception
    ) {
        String field =
            "requestBody";

        for (
            JsonMappingException.Reference reference
            : exception.getPath()
        ) {
            if (reference.getFieldName() != null) {
                field =
                    reference.getFieldName();
            }
        }

        return field;
    }
}
