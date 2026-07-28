package com.roompick.domain.reservation.validation;

import java.time.LocalDate;
import java.time.ZoneId;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 예약 요청의 체크인 날짜와 체크아웃 날짜가
 * 숙박 정책에 맞는지 검증하는 Validator입니다.
 *
 * 필수값 누락은 DTO의 @NotNull이 담당하고,
 * 이 Validator는 날짜가 모두 존재할 때 날짜 관계만 검증합니다.
 */
public class StayPeriodValidator
    implements ConstraintValidator<
    ValidStayPeriod,
    ReservationCreateRequestDto
    > {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    @Override
    public boolean isValid(
        ReservationCreateRequestDto request,
        ConstraintValidatorContext context
    ) {
        /*
         * 요청 객체 자체가 없거나 날짜가 누락된 경우에는
         * 각 필드의 @NotNull 검증이 오류를 처리하도록 넘깁니다.
         */
        if (
            request == null
                || request.checkInDate() == null
                || request.checkOutDate() == null
        ) {
            return true;
        }

        LocalDate checkInDate = request.checkInDate();
        LocalDate checkOutDate = request.checkOutDate();
        LocalDate today = LocalDate.now(SERVICE_ZONE_ID);

        boolean valid = true;

        /*
         * 클래스 단위 기본 오류 대신,
         * 프론트엔드가 바로 사용할 수 있도록
         * 실제 원인 필드에 오류를 연결합니다.
         */
        context.disableDefaultConstraintViolation();

        if (checkInDate.isBefore(today)) {
            addFieldError(
                context,
                "checkInDate",
                "체크인 날짜는 오늘 이전일 수 없습니다."
            );

            valid = false;
        }

        if (!checkInDate.isBefore(checkOutDate)) {
            addFieldError(
                context,
                "checkOutDate",
                "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다."
            );

            valid = false;
        }

        return valid;
    }

    /**
     * 클래스 단위 검증 오류를 지정한 요청 필드의 오류로 변환합니다.
     */
    private void addFieldError(
        ConstraintValidatorContext context,
        String field,
        String message
    ) {
        context
            .buildConstraintViolationWithTemplate(message)
            .addPropertyNode(field)
            .addConstraintViolation();
    }
}
