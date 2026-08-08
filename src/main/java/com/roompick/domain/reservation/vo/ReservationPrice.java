package com.roompick.domain.reservation.vo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 예약의 숙박 일수와 가격 계산 결과를 나타내는 값 객체입니다.
 *
 * 예약 생성과 예약 가능 여부 조회에서 동일한 계산 규칙을 사용합니다.
 */
public record ReservationPrice(

    int nightCount,
    long pricePerNight,
    long totalAmount

) {

    /**
     * 숙박 기간과 1박 가격을 기준으로 예약 금액을 계산합니다.
     */
    public static ReservationPrice calculate(
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long pricePerNight
    ) {
        validateStayPeriod(
            checkInDate,
            checkOutDate
        );
        validatePricePerNight(pricePerNight);

        int nightCount = Math.toIntExact(
            ChronoUnit.DAYS.between(
                checkInDate,
                checkOutDate
            )
        ) + 1;

        long totalAmount = Math.multiplyExact(
            pricePerNight,
            nightCount
        );

        return new ReservationPrice(
            nightCount,
            pricePerNight,
            totalAmount
        );
    }

    private static void validateStayPeriod(
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        if (
            checkInDate == null
                || checkOutDate == null
                || !checkInDate.isBefore(checkOutDate)
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_STAY_PERIOD
            );
        }
    }

    private static void validatePricePerNight(
        long pricePerNight
    ) {
        if (pricePerNight < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_ROOM_PRICE
            );
        }
    }
}
