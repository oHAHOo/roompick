package com.roompick.domain.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Mock 결제 승인 요청입니다.
 */
public record PaymentApproveRequestDto(

    @NotNull(message = "결제 금액은 필수입니다.")
    @PositiveOrZero(message = "결제 금액은 0원 이상이어야 합니다.")
    Long amount

) {
}
