package com.roompick.domain.payment.dto.response;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;

/**
 * 결제 준비 결과를 반환하는 응답 DTO입니다.
 *
 * 클라이언트는 응답으로 전달된 PortOne 설정값과
 * 결제 식별값을 사용해 PortOne 결제창을 호출합니다.
 */
public record PaymentPrepareResponseDto(
    Long paymentId,
    String portOnePaymentId,
    Long reservationId,
    long amount,
    PaymentStatus status,
    String storeId,
    String channelKey
) {

    /**
     * 기존 PortOne 연동 테스트와 생성 코드의
     * 컴파일 호환을 위한 생성자입니다.
     *
     * 실제 결제 준비 응답에서는 storeId와 channelKey가
     * 포함된 전체 생성자 또는 from 메서드를 사용합니다.
     */
    public PaymentPrepareResponseDto(
        Long paymentId,
        String portOnePaymentId,
        Long reservationId,
        long amount,
        PaymentStatus status
    ) {
        this(
            paymentId,
            portOnePaymentId,
            reservationId,
            amount,
            status,
            null,
            null
        );
    }

    /**
     * Payment와 PortOne 공개 설정을 이용해
     * 결제 준비 응답을 생성합니다.
     */
    public static PaymentPrepareResponseDto from(
        Payment payment,
        String storeId,
        String channelKey
    ) {
        return new PaymentPrepareResponseDto(
            payment.getId(),
            payment.getPortOnePaymentId(),
            payment.getReservation().getId(),
            payment.getAmount(),
            payment.getStatus(),
            storeId,
            channelKey
        );
    }
}
