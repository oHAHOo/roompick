package com.roompick.domain.payment.facade;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.roompick.domain.payment.dto.response.PaymentFailResponseDto;
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
     *
     * 결제 상태와 금액을 먼저 검증하여
     * 이미 처리된 결제에는 INVALID_PAYMENT_STATUS를 반환합니다.
     *
     * 이후 예약 소유자, 예약 상태, 결제 만료 여부를 검증하고
     * 예약을 확정합니다.
     *
     * 두 상태 변경은 같은 트랜잭션에서 처리되므로
     * 예약 검증에 실패하면 먼저 변경된 결제 상태도 롤백됩니다.
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
            LocalDateTime.now(SERVICE_ZONE_ID)
                .truncatedTo(ChronoUnit.MICROS);

        /*
         * 결제 상태와 요청 금액을 먼저 검증합니다.
         *
         * 이미 PAID 상태인 결제라면 예약 상태 검증보다 먼저
         * INVALID_PAYMENT_STATUS가 발생합니다.
         */
        Payment approvedPayment =
            paymentService.approvePayment(
                payment,
                requestedAmount,
                approvedAt
            );

        /*
         * 결제와 연결된 예약의 소유자, 상태, 만료 여부를
         * 검증한 뒤 예약을 확정합니다.
         */
        reservationService.confirmPayment(
            reservation,
            memberId,
            approvedAt
        );

        return PaymentApproveResponseDto.from(
            approvedPayment
        );
    }

    /**
     * READY 상태의 Mock 결제를 실패 처리하고
     * 연결된 예약을 취소합니다.
     *
     * 결제 상태를 먼저 검증하여 이미 처리된 결제에는
     * INVALID_PAYMENT_STATUS를 반환합니다.
     *
     * 결제와 예약 상태 변경은 하나의 트랜잭션에서 처리되므로
     * 예약 검증에 실패하면 Payment 변경도 롤백됩니다.
     */
    @Transactional
    public PaymentFailResponseDto failPayment(
        Long paymentId,
        Long memberId
    ) {
        Payment payment =
            paymentService.findById(paymentId);

        Reservation reservation =
            payment.getReservation();

        LocalDateTime failedAt =
            LocalDateTime.now(SERVICE_ZONE_ID)
                .truncatedTo(ChronoUnit.MICROS);

        /*
         * Payment가 READY 상태인지 먼저 검증한 뒤
         * FAILED 상태로 변경합니다.
         */
        Payment failedPayment =
            paymentService.failPayment(
                payment,
                failedAt
            );

        /*
         * 연결된 예약의 소유자와 상태를 검증한 뒤
         * CANCELED 상태로 변경합니다.
         *
         * 결제 대기 시간이 만료된 예약도 객실 점유를
         * 해제해야 하므로 만료 여부는 검사하지 않습니다.
         */
        reservationService.cancelByPaymentFailure(
            reservation,
            memberId,
            failedAt
        );

        return PaymentFailResponseDto.from(
            failedPayment
        );
    }
}
