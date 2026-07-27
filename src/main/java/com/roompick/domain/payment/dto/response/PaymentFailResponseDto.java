package com.roompick.domain.payment.dto.response;

import java.time.LocalDateTime;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;

/**
 * 결제 실패 처리 결과를 반환하는 DTO입니다.
 */
public record PaymentFailResponseDto(
    Long paymentId,
    Long reservationId,
    long amount,
    PaymentStatus paymentStatus,
    ReservationStatus reservationStatus,
    LocalDateTime failedAt,
    LocalDateTime canceledAt
) {

    public static PaymentFailResponseDto from(
        Payment payment
    ) {
        Reservation reservation =
            payment.getReservation();

        return new PaymentFailResponseDto(
            payment.getId(),
            reservation.getId(),
            payment.getAmount(),
            payment.getStatus(),
            reservation.getStatus(),
            payment.getFailedAt(),
            reservation.getCanceledAt()
        );
    }
}
