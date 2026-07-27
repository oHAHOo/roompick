package com.roompick.domain.payment.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.service.PaymentService;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.service.ReservationService;

import lombok.RequiredArgsConstructor;

/**
 * 예약과 결제 도메인의 결제 준비 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final ReservationService reservationService;
    private final PaymentService paymentService;

    /**
     * 회원의 예약을 확인하고 READY 상태의 결제를 생성합니다.
     */
    @Transactional
    public PaymentPrepareResponseDto preparePayment(
        Long reservationId,
        Long memberId
    ) {
        Reservation reservation =
            reservationService
                .findForPaymentPreparation(
                    reservationId,
                    memberId
                );

        Payment payment =
            paymentService.preparePayment(
                reservation
            );

        return PaymentPrepareResponseDto.from(
            payment
        );
    }
}
