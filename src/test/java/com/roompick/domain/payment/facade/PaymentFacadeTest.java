package com.roompick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
}
