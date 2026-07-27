package com.roompick.domain.payment.dto.response;

import java.time.LocalDateTime;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.reservation.entity.ReservationStatus;

/**
 * Mock 결제 승인 결과를 반환합니다.
 */
public record PaymentApproveResponseDto(

    Long paymentId,
    Long reservationId,
    long amount,
    PaymentStatus paymentStatus,
    ReservationStatus reservationStatus,
    LocalDateTime approvedAt

) {

    public static PaymentApproveResponseDto from(
        Payment payment
    ) {
        return new PaymentApproveResponseDto(
            payment.getId(),
            payment.getReservation().getId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getReservation().getStatus(),
            payment.getApprovedAt()
        );
    }
}
