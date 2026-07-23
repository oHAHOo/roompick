package com.roompick.global.common;

import com.roompick.global.common.ErrorResponseDto.ValidationErrorDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponseDto body = ErrorResponseDto.from(errorCode);

        ResponseEntity<ErrorResponseDto> response = ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        List<ValidationErrorDto> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationError)
                .toList();
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        ErrorResponseDto body = ErrorResponseDto.of(errorCode, errors);

        ResponseEntity<ErrorResponseDto> response = ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);
        return response;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(
        AccessDeniedException exception
    ) {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        log.warn(
            "Access denied. message={}",
            exception.getMessage()
        );

        ErrorResponseDto body = ErrorResponseDto.from(errorCode);

        ResponseEntity<ErrorResponseDto> response =
            ResponseEntity.status(errorCode.getHttpStatus())
                .body(body);

        return response;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception exception) {
        log.error("Unhandled exception", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponseDto body = ErrorResponseDto.from(errorCode);
        ResponseEntity<ErrorResponseDto> response = ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);
        return response;
    }

    private ValidationErrorDto toValidationError(FieldError fieldError) {
        return new ValidationErrorDto(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
