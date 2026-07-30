package com.roompick.global.common;

import java.util.List;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 발생시키는 공통 예외입니다.
 *
 * 대부분의 비즈니스 오류는 ErrorCode만 전달합니다.
 * 요청 필드에 표시할 구체적인 추가 정보가 있는 경우에만
 * BusinessFieldError 목록을 함께 전달합니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<BusinessFieldError> fieldErrors;

    /**
     * 특정 요청 필드와 연결되지 않는 일반 비즈니스 오류를 생성합니다.
     *
     * 이 경우 공통 응답의 errors는 빈 배열로 반환됩니다.
     */
    public BusinessException(ErrorCode errorCode) {
        this(
            errorCode,
            List.of()
        );
    }

    /**
     * 상단 에러 메시지보다 구체적인 필드 안내가 있는
     * 비즈니스 오류를 생성합니다.
     */
    public BusinessException(
        ErrorCode errorCode,
        List<BusinessFieldError> fieldErrors
    ) {
        super(errorCode.getMessage());

        this.errorCode = errorCode;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /**
     * 비즈니스 오류와 연결된 요청 필드의 상세 정보를 전달합니다.
     */
    public record BusinessFieldError(
        String field,
        String message
    ) {
    }
}
