package com.roompick.domain.payment.facade;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.payment.dto.response.PaymentApproveResponseDto;
import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.service.PaymentService;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.service.ReservationService;

import lombok.RequiredArgsConstructor;

/**
 * 예약과 결제 도메인의 결제 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

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

    /**
     * Mock 결제를 승인하고 예약을 확정합니다.
     */
    @Transactional
    public PaymentApproveResponseDto approvePayment(
        Long paymentId,
        Long memberId,
        long requestedAmount
    ) {
        Payment payment =
            paymentService.findById(paymentId);

        Reservation reservation =
            payment.getReservation();

        LocalDateTime approvedAt =
            LocalDateTime.now(SERVICE_ZONE_ID);

        reservationService.confirmPayment(
            reservation,
            memberId,
            approvedAt
        );

        Payment approvedPayment =
            paymentService.approvePayment(
                payment,
                requestedAmount,
                approvedAt
            );

        return PaymentApproveResponseDto.from(
            approvedPayment
        );
    }
}
