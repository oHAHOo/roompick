package com.roompick.domain.payment.dto.response;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;

/**
 * 결제 준비 결과를 반환하는 DTO입니다.
 */
public record PaymentPrepareResponseDto(

    Long paymentId,
    Long reservationId,
    long amount,
    PaymentStatus status

) {

    public static PaymentPrepareResponseDto from(
        Payment payment
    ) {
        return new PaymentPrepareResponseDto(
            payment.getId(),
            payment.getReservation().getId(),
            payment.getAmount(),
            payment.getStatus()
        );
    }
}
