package com.roompick.domain.payment.client.portone.dto.response;

import java.time.LocalDateTime;

/**
 * PortOne 결제 정보 검증이 완료된 결과입니다.
 */
public record PortOnePaymentVerificationResultDto(

    String transactionId,
    long amount,
    LocalDateTime paidAt

) {
}
