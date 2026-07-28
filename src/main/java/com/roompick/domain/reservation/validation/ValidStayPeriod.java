package com.roompick.domain.reservation.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 예약 요청의 체크인·체크아웃 날짜 관계를 검증하는
 * 클래스 단위 Bean Validation 어노테이션입니다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StayPeriodValidator.class)
@Documented
public @interface ValidStayPeriod {

    /**
     * 별도의 필드 메시지를 만들지 못한 경우 사용하는 기본 메시지입니다.
     */
    String message() default "숙박 기간이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
