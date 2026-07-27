package com.roompick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.roompick.domain.payment.dto.response.PaymentApproveResponseDto;
import com.roompick.domain.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.service.PaymentService;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.service.ReservationService;

import java.time.LocalDateTime;

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
    @DisplayName("예약을 검증한 뒤 결제를 준비한다")
    void 예약을_검증한_뒤_결제를_준비한다() {
        // given
        Long reservationId = 1L;
        Long memberId = 10L;

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
            .willReturn(100L);

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getId())
            .willReturn(reservationId);

        given(payment.getAmount())
            .willReturn(200000L);

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
            .isEqualTo(100L);

        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.amount())
            .isEqualTo(200000L);

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
            .preparePayment(reservation);
    }

    @Test
    @DisplayName("결제를 승인하고 예약을 확정한다")
    void approvePaymentAndConfirmReservation() {
        // given
        Long paymentId = 100L;
        Long reservationId = 1L;
        Long memberId = 10L;

        given(
            paymentService.findById(paymentId)
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            reservationService.confirmPayment(
                eq(reservation),
                eq(memberId),
                any(LocalDateTime.class)
            )
        ).willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(200_000L),
                any(LocalDateTime.class)
            )
        ).willReturn(payment);

        given(payment.getId())
            .willReturn(paymentId);

        given(reservation.getId())
            .willReturn(reservationId);

        given(payment.getAmount())
            .willReturn(200_000L);

        given(payment.getStatus())
            .willReturn(PaymentStatus.PAID);

        given(reservation.getStatus())
            .willReturn(ReservationStatus.CONFIRMED);

        given(payment.getApprovedAt())
            .willReturn(
                LocalDateTime.of(
                    2026,
                    7,
                    24,
                    20,
                    0
                )
            );

        // when
        PaymentApproveResponseDto response =
            paymentFacade.approvePayment(
                paymentId,
                memberId,
                200_000L
            );

        // then
        assertThat(response.paymentId())
            .isEqualTo(paymentId);

        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(response.reservationStatus())
            .isEqualTo(ReservationStatus.CONFIRMED);

        then(reservationService)
            .should()
            .confirmPayment(
                eq(reservation),
                eq(memberId),
                any(LocalDateTime.class)
            );

        then(paymentService)
            .should()
            .approvePayment(
                eq(payment),
                eq(200_000L),
                any(LocalDateTime.class)
            );
    }
}
