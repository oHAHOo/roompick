package com.roompick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;

import com.roompick.domain.payment.dto.response.PaymentFailResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.payment.dto.response.PaymentApproveResponseDto;
import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.service.PaymentService;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private Reservation reservation;

    @Mock
    private Payment payment;

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName("회원의 예약을 확인하고 READY 상태의 결제를 준비한다")
    void preparePayment() {
        // given
        Long reservationId = 1L;
        Long memberId = 10L;
        Long paymentId = 100L;

        given(
            reservationService
                .findForPaymentPreparation(
                    reservationId,
                    memberId
                )
        ).willReturn(reservation);

        given(
            paymentService.preparePayment(
                reservation
            )
        ).willReturn(payment);

        given(payment.getId())
            .willReturn(paymentId);

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getId())
            .willReturn(reservationId);

        given(payment.getAmount())
            .willReturn(200_000L);

        given(payment.getStatus())
            .willReturn(PaymentStatus.READY);

        // when
        PaymentPrepareResponseDto response =
            paymentFacade.preparePayment(
                reservationId,
                memberId
            );

        // then
        assertThat(response.paymentId())
            .isEqualTo(paymentId);

        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.amount())
            .isEqualTo(200_000L);

        assertThat(response.status())
            .isEqualTo(PaymentStatus.READY);

        then(reservationService)
            .should()
            .findForPaymentPreparation(
                reservationId,
                memberId
            );

        then(paymentService)
            .should()
            .preparePayment(
                reservation
            );
    }

    @Test
    @DisplayName("결제 상태와 금액을 먼저 검증한 뒤 예약을 확정한다")
    void approvePaymentAndConfirmReservation() {
        // given
        Long paymentId = 100L;
        Long reservationId = 1L;
        Long memberId = 10L;
        long requestedAmount = 200_000L;

        LocalDateTime approvedAt =
            LocalDateTime.of(
                2026,
                7,
                27,
                15,
                30
            );

        given(
            paymentService.findById(paymentId)
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(requestedAmount),
                any(LocalDateTime.class)
            )
        ).willReturn(payment);

        given(
            reservationService.confirmPayment(
                eq(reservation),
                eq(memberId),
                any(LocalDateTime.class)
            )
        ).willReturn(reservation);

        given(payment.getId())
            .willReturn(paymentId);

        given(reservation.getId())
            .willReturn(reservationId);

        given(payment.getAmount())
            .willReturn(requestedAmount);

        given(payment.getStatus())
            .willReturn(PaymentStatus.PAID);

        given(reservation.getStatus())
            .willReturn(
                ReservationStatus.CONFIRMED
            );

        given(payment.getApprovedAt())
            .willReturn(approvedAt);

        ArgumentCaptor<LocalDateTime>
            paymentApprovedAtCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        ArgumentCaptor<LocalDateTime>
            reservationApprovedAtCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        // when
        PaymentApproveResponseDto response =
            paymentFacade.approvePayment(
                paymentId,
                memberId,
                requestedAmount
            );

        // then
        assertThat(response.paymentId())
            .isEqualTo(paymentId);

        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.amount())
            .isEqualTo(requestedAmount);

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(response.approvedAt())
            .isEqualTo(approvedAt);

        /*
         * Payment 승인 후 Reservation 확정 순서로
         * 호출되는지 검증합니다.
         */
        InOrder inOrder =
            inOrder(
                paymentService,
                reservationService
            );

        inOrder.verify(paymentService)
            .approvePayment(
                eq(payment),
                eq(requestedAmount),
                paymentApprovedAtCaptor.capture()
            );

        inOrder.verify(reservationService)
            .confirmPayment(
                eq(reservation),
                eq(memberId),
                reservationApprovedAtCaptor.capture()
            );

        /*
         * 결제와 예약에 동일한 처리 시각이
         * 전달되는지 검증합니다.
         */
        assertThat(
            paymentApprovedAtCaptor.getValue()
        ).isEqualTo(
            reservationApprovedAtCaptor.getValue()
        );
    }

    @Test
    @DisplayName("이미 승인된 결제는 예약 검증 전에 거절한다")
    void rejectDuplicatedApprovalBeforeReservationValidation() {
        // given
        Long paymentId = 100L;
        Long memberId = 10L;
        long requestedAmount = 200_000L;

        given(
            paymentService.findById(paymentId)
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(requestedAmount),
                any(LocalDateTime.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.approvePayment(
                    paymentId,
                    memberId,
                    requestedAmount
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        /*
         * 결제 상태 검증에서 실패했으므로
         * 예약 확정 Service는 호출되지 않아야 합니다.
         */
        verifyNoInteractions(
            reservationService
        );
    }

    @Test
    @DisplayName("결제 금액이 다르면 예약 확정 처리를 호출하지 않는다")
    void rejectAmountMismatchBeforeReservationConfirmation() {
        // given
        Long paymentId = 100L;
        Long memberId = 10L;
        long wrongAmount = 190_000L;

        given(
            paymentService.findById(paymentId)
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(wrongAmount),
                any(LocalDateTime.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.approvePayment(
                    paymentId,
                    memberId,
                    wrongAmount
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            );

        verifyNoInteractions(
            reservationService
        );
    }

    @Test
    @DisplayName("결제 실패 시 Payment 실패 처리 후 Reservation을 취소한다")
    void failPayment() {
        // given
        Long paymentId = 1L;
        Long memberId = 10L;

        given(paymentService.findById(paymentId))
            .willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.failPayment(
                eq(payment),
                any(LocalDateTime.class)
            )
        ).willReturn(payment);

        given(payment.getId())
            .willReturn(paymentId);

        given(payment.getAmount())
            .willReturn(200_000L);

        given(payment.getStatus())
            .willReturn(PaymentStatus.FAILED);

        given(payment.getFailedAt())
            .willReturn(
                LocalDateTime.of(
                    2026,
                    7,
                    27,
                    16,
                    0
                )
            );

        given(reservation.getId())
            .willReturn(100L);

        given(reservation.getStatus())
            .willReturn(
                ReservationStatus.CANCELED
            );

        given(reservation.getCanceledAt())
            .willReturn(
                LocalDateTime.of(
                    2026,
                    7,
                    27,
                    16,
                    0
                )
            );

        // when
        PaymentFailResponseDto result =
            paymentFacade.failPayment(
                paymentId,
                memberId
            );

        // then
        ArgumentCaptor<LocalDateTime>
            paymentTimeCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        ArgumentCaptor<LocalDateTime>
            reservationTimeCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        InOrder inOrder =
            inOrder(
                paymentService,
                reservationService
            );

        inOrder.verify(paymentService)
            .failPayment(
                eq(payment),
                paymentTimeCaptor.capture()
            );

        inOrder.verify(reservationService)
            .cancelByPaymentFailure(
                eq(reservation),
                eq(memberId),
                reservationTimeCaptor.capture()
            );

        assertThat(paymentTimeCaptor.getValue())
            .isEqualTo(
                reservationTimeCaptor.getValue()
            );

        assertThat(result.paymentId())
            .isEqualTo(paymentId);

        assertThat(result.reservationId())
            .isEqualTo(100L);

        assertThat(result.paymentStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(result.reservationStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );
    }

    @Test
    @DisplayName("결제 상태 검증에 실패하면 예약 취소를 호출하지 않는다")
    void paymentFailureValidationStopsBeforeReservationCancellation() {
        // given
        Long paymentId = 1L;
        Long memberId = 10L;

        given(paymentService.findById(paymentId))
            .willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.failPayment(
                eq(payment),
                any(LocalDateTime.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.failPayment(
                    paymentId,
                    memberId
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        then(reservationService)
            .shouldHaveNoInteractions();
    }
}
