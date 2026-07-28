package com.roompick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import java.time.LocalDateTime;

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

    @Test
    @DisplayName("READY 상태의 결제를 승인하면 PAID 상태가 된다")
    void readyPaymentCanBeApproved() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        LocalDateTime approvedAt =
            LocalDateTime.of(
                2026,
                7,
                24,
                20,
                0
            );

        // when
        payment.approve(
            200_000L,
            approvedAt
        );

        // then
        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(payment.getApprovedAt())
            .isEqualTo(approvedAt);

        assertThat(payment.getFailedAt())
            .isNull();
    }

    @Test
    @DisplayName("요청 금액이 저장된 결제 금액과 다르면 승인할 수 없다")
    void rejectApprovalWhenAmountDoesNotMatch() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> payment.approve(
                    190_000L,
                    LocalDateTime.now()
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            );

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        assertThat(payment.getApprovedAt())
            .isNull();
    }

    @Test
    @DisplayName("이미 승인된 결제는 다시 승인할 수 없다")
    void rejectDuplicatedApproval() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        payment.approve(
            200_000L,
            LocalDateTime.now()
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> payment.approve(
                    200_000L,
                    LocalDateTime.now()
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("결제 승인 시각이 없으면 승인할 수 없다")
    void rejectApprovalWithoutApprovedAt() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> payment.approve(
                    200_000L,
                    null
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("READY 상태의 결제를 실패 처리하면 FAILED 상태가 된다")
    void readyPaymentCanBeFailed() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        LocalDateTime failedAt =
            LocalDateTime.of(
                2026,
                7,
                27,
                14,
                0
            );

        // when
        payment.fail(failedAt);

        // then
        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getFailedAt())
            .isEqualTo(failedAt);

        assertThat(payment.getApprovedAt())
            .isNull();
    }

    @Test
    @DisplayName("이미 승인된 결제는 실패 처리할 수 없다")
    void paidPaymentCannotBeFailed() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        LocalDateTime approvedAt =
            LocalDateTime.of(
                2026,
                7,
                27,
                14,
                0
            );

        payment.approve(
            200_000L,
            approvedAt
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> payment.fail(
                    LocalDateTime.of(
                        2026,
                        7,
                        27,
                        14,
                        1
                    )
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(payment.getApprovedAt())
            .isEqualTo(approvedAt);

        assertThat(payment.getFailedAt())
            .isNull();
    }

    @Test
    @DisplayName("이미 실패한 결제는 다시 실패 처리할 수 없다")
    void failedPaymentCannotBeFailedAgain() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        LocalDateTime firstFailedAt =
            LocalDateTime.of(
                2026,
                7,
                27,
                14,
                0
            );

        payment.fail(firstFailedAt);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> payment.fail(
                    LocalDateTime.of(
                        2026,
                        7,
                        27,
                        14,
                        1
                    )
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getFailedAt())
            .isEqualTo(firstFailedAt);
    }

    @Test
    @DisplayName("결제 실패 시각이 없으면 실패 처리할 수 없다")
    void paymentCannotBeFailedWithoutFailedAt() {
        // given
        given(reservation.getTotalAmount())
            .willReturn(200_000L);

        Payment payment =
            Payment.create(reservation);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> payment.fail(null),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        assertThat(payment.getFailedAt())
            .isNull();
    }
}
