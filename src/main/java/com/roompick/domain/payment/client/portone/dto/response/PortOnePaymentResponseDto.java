package com.roompick.domain.payment.client.portone.dto.response;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PortOne V2 결제 단건 조회 응답입니다.
 *
 * 결제 완료 검증에 필요한 필드만 받습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponseDto(

    String status,
    String id,
    String transactionId,
    Amount amount,
    OffsetDateTime paidAt

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
        Long total
    ) {
    }
}
