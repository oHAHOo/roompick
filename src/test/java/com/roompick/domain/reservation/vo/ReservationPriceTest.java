package com.roompick.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 예약 숙박 일수와 총액 계산 규칙을 검증합니다.
 */
class ReservationPriceTest {

    @Test
    @DisplayName("숙박 일수와 총액을 계산한다")
    void calculateReservationPrice() {
        // given
        LocalDate checkInDate =
            LocalDate.of(2026, 8, 10);
        LocalDate checkOutDate =
            LocalDate.of(2026, 8, 12);

        // when
        ReservationPrice reservationPrice =
            ReservationPrice.calculate(
                checkInDate,
                checkOutDate,
                100_000L
            );

        // then
        assertThat(reservationPrice.nightCount())
            .isEqualTo(2);
        assertThat(reservationPrice.pricePerNight())
            .isEqualTo(100_000L);
        assertThat(reservationPrice.totalAmount())
            .isEqualTo(200_000L);
    }

    @Test
    @DisplayName("숙박 기간이 올바르지 않으면 계산할 수 없다")
    void rejectInvalidStayPeriod() {
        // given
        LocalDate sameDate =
            LocalDate.of(2026, 8, 10);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> ReservationPrice.calculate(
                    sameDate,
                    sameDate,
                    100_000L
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_STAY_PERIOD);
    }

    @Test
    @DisplayName("1박 가격이 음수이면 계산할 수 없다")
    void rejectNegativePricePerNight() {
        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> ReservationPrice.calculate(
                    LocalDate.of(2026, 8, 10),
                    LocalDate.of(2026, 8, 12),
                    -1L
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_ROOM_PRICE);
    }
}
