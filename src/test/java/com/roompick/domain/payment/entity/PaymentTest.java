package com.roompick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PaymentTest {

    @Mock
    private Reservation reservation;

    @Test
    @DisplayName("예약 금액으로 READY 상태의 결제를 생성한다")
    void 예약_금액으로_READY_상태의_결제를_생성한다() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200000L);

        // when
        Payment payment =
            Payment.create(reservation);

        // then
        assertThat(payment.getReservation())
            .isSameAs(reservation);

        assertThat(payment.getAmount())
            .isEqualTo(200000L);

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        assertThat(payment.getApprovedAt())
            .isNull();

        assertThat(payment.getFailedAt())
            .isNull();
    }

    @Test
    @DisplayName("0원 예약도 결제를 준비할 수 있다")
    void 가격이_0원_예약도_결제를_준비할_수_있다() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(0L);

        // when
        Payment payment =
            Payment.create(reservation);

        // then
        assertThat(payment.getAmount())
            .isZero();

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("예약이 없으면 결제를 생성할 수 없다")
    void 예약이_없으면_결제를_생성할_수_없다() {
        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> Payment.create(null)
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_NOT_FOUND
            );
    }

    @Test
    @DisplayName("예약 금액이 음수이면 결제를 생성할 수 없다")
    void 예약_금액이_음수이면_결제를_생성할_수_없다() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(-1L);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> Payment.create(reservation)
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_AMOUNT
            );
    }
}
