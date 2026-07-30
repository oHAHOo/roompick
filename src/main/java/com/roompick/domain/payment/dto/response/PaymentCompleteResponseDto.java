package com.roompick.domain.payment.dto.response;

import java.time.LocalDateTime;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.reservation.entity.ReservationStatus;

/**
 * PortOne 결제 완료 처리 결과를 반환하는 DTO입니다.
 */
public record PaymentCompleteResponseDto(

    Long paymentId,
    String portOnePaymentId,
    String portOneTransactionId,
    Long reservationId,
    long amount,
    PaymentStatus paymentStatus,
    ReservationStatus reservationStatus,
    LocalDateTime approvedAt

) {

    public static PaymentCompleteResponseDto from(
        Payment payment
    ) {
        return new PaymentCompleteResponseDto(
            payment.getId(),
            payment.getPortOnePaymentId(),
            payment.getPortOneTransactionId(),
            payment.getReservation().getId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getReservation().getStatus(),
            payment.getApprovedAt()
        );
    }
}
